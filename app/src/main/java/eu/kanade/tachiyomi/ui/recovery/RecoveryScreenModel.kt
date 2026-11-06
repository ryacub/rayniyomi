package eu.kanade.tachiyomi.ui.recovery

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import eu.kanade.presentation.util.StateViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.domain.recovery.RecoveryApplyRunner
import tachiyomi.domain.recovery.RecoverySearcher
import tachiyomi.domain.recovery.RecoverySessionEntries
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryState
import java.util.UUID

class RecoveryScreenModel(
    val media: RecoveryMedia,
    private val sourceId: Long?,
    private val originalIds: List<Long>,
    private val backend: RecoveryBackend,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : StateViewModel<RecoveryScreenModel.State>(State()) {

    private val repository = backend.repository
    private val searcher = RecoverySearcher(repository, backend.search)
    private val runner = RecoveryApplyRunner(repository, backend.applier, backend.preparer, backend::copyCover)

    private var work: Job? = null
    private var entriesJob: Job? = null

    init {
        viewModelScope.launch(ioDispatcher) { load() }
    }

    private suspend fun load() {
        val originals = backend.originals(sourceId, originalIds)
        val sources = backend.destinationSources(sourceId)
        mutableState.update {
            it.copy(
                originals = originals,
                selectedOriginals = originals.map(RecoveryOriginal::id).toSet(),
                sources = sources,
            )
        }
        val open = repository.getLatestOpenSession()
        if (open != null) {
            mutableState.update { it.copy(keepOriginal = open.keepOriginal) }
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

    fun searchAgain(entryId: Long, query: String) = launchWork(Busy.Searching) {
        val sessionId = state.value.sessionId ?: return@launchWork
        if (!repository.setQuery(entryId, query)) return@launchWork
        clearPreview(entryId)
        searcher.run(sessionId, newToken())
        refreshEntries(sessionId)
    }

    fun choose(entryId: Long, candidate: RecoveryCandidate) {
        val entry = state.value.entries.firstOrNull { it.id == entryId } ?: return
        mutableState.update {
            it.copy(chosen = it.chosen + (entryId to candidate), previewErrors = it.previewErrors - entryId)
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                val preview = backend.preview(entry.originalId, candidate)
                mutableState.update { it.copy(previews = it.previews + (entryId to preview)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e::class.simpleName.orEmpty()
                mutableState.update { it.copy(previewErrors = it.previewErrors + (entryId to message)) }
            }
        }
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
        mutableState.update {
            it.copy(
                step = Step.Select,
                sessionId = null,
                entries = emptyList(),
                chosen = emptyMap(),
                previews = emptyMap(),
                previewErrors = emptyMap(),
                coverFailures = emptyMap(),
            )
        }
    }

    private fun launchWork(busy: Busy, block: suspend () -> Unit) {
        if (work?.isActive == true) return
        mutableState.update { it.copy(busy = busy) }
        work = viewModelScope.launch(ioDispatcher) {
            try {
                block()
            } finally {
                mutableState.update { it.copy(busy = null) }
            }
        }
    }

    private fun clearPreview(entryId: Long) = mutableState.update {
        it.copy(
            chosen = it.chosen - entryId,
            previews = it.previews - entryId,
            previewErrors =
            it.previewErrors - entryId,
        )
    }

    private fun newToken() = UUID.randomUUID().toString()

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
        val fullyRecovered: Boolean get() = completed > 0 && failed == 0 && unresolved == 0 && unsupported == 0
    }

    @Immutable
    data class State(
        val step: Step = Step.Loading,
        val sessionId: Long? = null,
        val originals: List<RecoveryOriginal> = emptyList(),
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
    ) {
        val summary: Summary
            get() = Summary(
                completed = entries.count { it.state == RecoveryState.COMPLETED },
                failed = entries.count { it.state == RecoveryState.FAILED },
                unresolved = entries.count { it.state in unresolvedStates },
                unsupported = entries.count { it.state == RecoveryState.UNSUPPORTED },
                skipped = entries.count { it.state == RecoveryState.SKIPPED },
            )

        fun canApprove(entryId: Long): Boolean {
            val entry = entries.firstOrNull { it.id == entryId } ?: return false
            val preview = previews[entryId] ?: return false
            return entryId in chosen && !preview.destination.seasons && entry.state !in RecoveryState.locked
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
