package eu.kanade.tachiyomi.ui.recovery

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.presentation.util.StateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.recovery.RecoveryApplyRunner
import tachiyomi.domain.recovery.RecoverySearcher
import tachiyomi.domain.recovery.RecoverySessionEntries
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class RecoveryScreenModel(
    val media: RecoveryMedia,
    private val sourceId: Long?,
    private val originalIds: List<Long>,
    private val backend: RecoveryBackend,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateViewModel<RecoveryScreenModel.State>(State()) {

    @AssistedInject
    constructor(
        @Assisted media: RecoveryMedia,
        @Assisted sourceId: Long?,
        @Assisted originalIds: List<Long>,
        backends: RecoveryBackends,
    ) : this(media, sourceId, originalIds, backends.of(media))

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(media: RecoveryMedia, sourceId: Long?, originalIds: List<Long>): RecoveryScreenModel
    }

    private val repository = backend.repository
    private val searcher = RecoverySearcher(repository, backend.search)
    private val runner = RecoveryApplyRunner(repository, backend.applier, backend.preparer, backend::copyCover)

    private var work: Job? = null
    private var entriesJob: Job? = null
    private val previewJobs = ConcurrentHashMap<Long, Job>()

    init {
        viewModelScope.launch(ioDispatcher) {
            try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Recovery failed to load" }
                mutableState.update { it.copy(step = Step.Select, workError = e.messageOrName()) }
            }
        }
    }

    private suspend fun load() {
        repository.reconcileAfterProcessStart()
        val originals = backend.originals(sourceId, originalIds)
        val excluded = originals.map(RecoveryOriginal::sourceId).toSet() + setOfNotNull(sourceId)
        val sources = backend.destinationSources(excluded)
        mutableState.update {
            it.copy(
                originals = originals,
                selectedOriginals = originals.map(RecoveryOriginal::id).toSet(),
                sources = sources,
                titles = it.titles + originals.associate { original -> original.id to original.title },
            )
        }
        val requested = originals.map(RecoveryOriginal::id)
        val open = repository.getLatestOpenSession(requested)
        if (open != null) {
            val openOriginals = repository.getEntries(open.id).map { it.originalId }
            val titles = backend.originals(null, openOriginals).associate { it.id to it.title }
            mutableState.update {
                it.copy(
                    keepOriginal = open.keepOriginal,
                    titles = it.titles + titles,
                    notInSession = requested.count { id -> id !in openOriginals },
                )
            }
            attach(open.id)
        } else {
            mutableState.update { it.copy(step = Step.Select) }
        }
    }

    private fun attach(sessionId: Long) {
        entriesJob?.cancel()
        mutableState.update { it.copy(sessionId = sessionId, step = Step.Review) }
        entriesJob = viewModelScope.launch(ioDispatcher) {
            repository.subscribeEntries(sessionId).collect { entries ->
                mutableState.update { it.copy(entries = entries) }
            }
        }
    }

    fun toggleOriginal(id: Long) = mutableState.update {
        it.copy(selectedOriginals = it.selectedOriginals.toggle(id))
    }

    fun toggleSource(id: Long) = mutableState.update {
        it.copy(selectedSources = it.selectedSources.toggle(id))
    }

    fun setKeepOriginal(keep: Boolean) = mutableState.update { it.copy(keepOriginal = keep) }

    fun findReplacements() = launchWork(Busy.Searching) {
        val state = state.value
        if (state.selectedSources.isEmpty() || state.selectedOriginals.isEmpty()) return@launchWork
        val originals = state.originals.filter { it.id in state.selectedOriginals }
        val sessionId = repository.createSession(
            destinationSourceIds = state.sources.map { it.id }.filter { it in state.selectedSources },
            keepOriginal = state.keepOriginal,
            entries = RecoverySessionEntries.from(
                originals.map { RecoverySessionEntries.Original(it.id, it.title, it.seasons) },
            ),
            createdAt = now(),
        )
        attach(sessionId)
        searcher.run(sessionId, newToken())
        refreshEntries(sessionId)
    }

    fun searchAgain(entryId: Long, query: String) {
        clearPreview(entryId)
        launchWork(Busy.Searching) {
            val sessionId = state.value.sessionId ?: return@launchWork
            if (!repository.setQuery(entryId, query)) return@launchWork
            searcher.run(sessionId, newToken())
            refreshEntries(sessionId)
        }
    }

    fun searchRemaining() = launchWork(Busy.Searching) {
        val sessionId = state.value.sessionId ?: return@launchWork
        searcher.run(sessionId, newToken())
        refreshEntries(sessionId)
    }

    fun choose(entryId: Long, candidate: RecoveryCandidate) {
        val entry = state.value.entries.firstOrNull { it.id == entryId } ?: return
        previewJobs.remove(entryId)?.cancel()
        mutableState.update {
            it.copy(
                chosen = it.chosen + (entryId to candidate),
                previews = it.previews - entryId,
                previewErrors = it.previewErrors - entryId,
            )
        }
        val job = viewModelScope.launch(ioDispatcher) {
            try {
                val preview = backend.preview(entry.originalId, candidate)
                mutableState.update {
                    if (it.chosen[entryId] != candidate) it else it.copy(previews = it.previews + (entryId to preview))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.update {
                    if (it.chosen[entryId] != candidate) {
                        it
                    } else {
                        it.copy(previewErrors = it.previewErrors + (entryId to e.messageOrName()))
                    }
                }
            }
        }
        previewJobs[entryId] = job
        job.invokeOnCompletion { previewJobs.remove(entryId, job) }
    }

    fun approve(entryId: Long) {
        val state = state.value
        if (!state.canApprove(entryId)) return
        val candidate = state.chosen.getValue(entryId)
        val preview = state.previews.getValue(entryId)
        viewModelScope.launch(ioDispatcher) { repository.approve(entryId, candidate, preview.pairs) }
    }

    fun skip(entryId: Long) {
        viewModelScope.launch(ioDispatcher) { repository.skip(entryId) }
    }

    fun reopen(entryId: Long) {
        clearPreview(entryId)
        viewModelScope.launch(ioDispatcher) { repository.reopen(entryId) }
    }

    fun applySelected() = launchWork(Busy.Applying) {
        val sessionId = state.value.sessionId ?: return@launchWork
        val report = runner.run(sessionId, newToken())
        showResult(sessionId, report.coverFailures)
    }

    fun retryFailed() = launchWork(Busy.Applying) {
        val sessionId = state.value.sessionId ?: return@launchWork
        searcher.run(sessionId, newToken())
        val report = runner.run(sessionId, newToken())
        showResult(sessionId, report.coverFailures)
    }

    private suspend fun refreshEntries(sessionId: Long) {
        val entries = repository.getEntries(sessionId)
        mutableState.update { it.copy(entries = entries) }
    }

    private suspend fun showResult(sessionId: Long, coverFailures: Map<Long, String>) {
        val entries = repository.getEntries(sessionId)
        mutableState.update {
            it.copy(entries = entries, coverFailures = it.coverFailures + coverFailures, step = Step.Result)
        }
    }

    fun reviewUnresolved() = mutableState.update { it.copy(step = Step.Review) }

    fun cancel() {
        work?.cancel()
    }

    fun startAnother() {
        work?.cancel()
        entriesJob?.cancel()
        previewJobs.values.forEach(Job::cancel)
        previewJobs.clear()
        mutableState.update {
            it.copy(
                step = Step.Select,
                sessionId = null,
                entries = emptyList(),
                chosen = emptyMap(),
                previews = emptyMap(),
                previewErrors = emptyMap(),
                coverFailures = emptyMap(),
                workError = null,
                notInSession = 0,
            )
        }
    }

    private fun launchWork(busy: Busy, block: suspend () -> Unit) {
        if (state.value.busy != null) return
        mutableState.update { it.copy(busy = busy, workError = null) }
        val job = viewModelScope.launch(ioDispatcher, start = CoroutineStart.LAZY) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Recovery work failed" }
                mutableState.update { it.copy(workError = e.messageOrName()) }
                state.value.sessionId?.let { runCatching { refreshEntries(it) } }
            }
        }
        work = job
        job.invokeOnCompletion {
            mutableState.update { it.copy(busy = null) }
        }
        job.start()
    }

    private fun clearPreview(entryId: Long) {
        previewJobs.remove(entryId)?.cancel()
        mutableState.update {
            it.copy(
                chosen = it.chosen - entryId,
                previews = it.previews - entryId,
                previewErrors = it.previewErrors - entryId,
            )
        }
    }

    private fun newToken() = UUID.randomUUID().toString()

    private fun Exception.messageOrName() = message ?: this::class.simpleName.orEmpty()

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    enum class Step { Loading, Select, Review, Result }

    enum class Busy { Searching, Applying }

    @Immutable
    data class Summary(
        val completed: Int,
        val failed: Int,
        val unresolved: Int,
        val unsupported: Int,
        val skipped: Int,
    ) {
        val needsAttention: Int get() = failed + unresolved + unsupported

        val fullyRecovered: Boolean get() = completed > 0 && needsAttention == 0 && skipped == 0
    }

    @Immutable
    data class State(
        val step: Step = Step.Loading,
        val sessionId: Long? = null,
        val originals: List<RecoveryOriginal> = emptyList(),
        val titles: Map<Long, String> = emptyMap(),
        val selectedOriginals: Set<Long> = emptySet(),
        val sources: List<RecoverySourceOption> = emptyList(),
        val selectedSources: Set<Long> = emptySet(),
        val keepOriginal: Boolean = true,
        val entries: List<RecoveryEntry> = emptyList(),
        val chosen: Map<Long, RecoveryCandidate> = emptyMap(),
        val previews: Map<Long, RecoveryPreview> = emptyMap(),
        val previewErrors: Map<Long, String> = emptyMap(),
        val coverFailures: Map<Long, String> = emptyMap(),
        val busy: Busy? = null,
        val workError: String? = null,
        val notInSession: Int = 0,
    ) {
        val summary: Summary
            get() = Summary(
                completed = entries.count { it.state == RecoveryState.COMPLETED },
                failed = entries.count { it.state == RecoveryState.FAILED },
                unresolved = entries.count { it.state in unresolvedStates },
                unsupported = entries.count { it.state == RecoveryState.UNSUPPORTED },
                skipped = entries.count { it.state == RecoveryState.SKIPPED },
            )

        val searchableCount: Int
            get() = entries.count {
                it.selected && it.choice == null && it.candidates.isEmpty() && it.state in RecoveryState.searchable
            }

        val applicableCount: Int
            get() = entries.count { it.selected && it.choice != null && it.state in RecoveryState.applicable }

        fun titleOf(entry: RecoveryEntry): String = titles[entry.originalId] ?: entry.searchQuery

        fun canApprove(entryId: Long): Boolean {
            val entry = entries.firstOrNull { it.id == entryId } ?: return false
            val candidate = chosen[entryId] ?: return false
            val destination = previews[entryId]?.destination ?: return false
            return destination.sourceId == candidate.sourceId &&
                destination.url == candidate.url &&
                !destination.seasons &&
                entry.state !in RecoveryState.locked
        }

        private companion object {
            val unresolvedStates = setOf(
                RecoveryState.SELECTED,
                RecoveryState.SEARCHING,
                RecoveryState.NEEDS_REVIEW,
                RecoveryState.MISSING,
                RecoveryState.READY,
                RecoveryState.APPLYING,
                RecoveryState.INTERRUPTED,
            )
        }
    }
}
