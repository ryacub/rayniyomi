package eu.kanade.tachiyomi.ui.entries.manga

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastAny
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import eu.kanade.core.preference.asState
import eu.kanade.domain.entries.manga.interactor.GetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.SetExcludedScanlators
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.chaptersFiltered
import eu.kanade.domain.entries.manga.model.downloadedFilter
import eu.kanade.domain.items.chapter.interactor.GetAvailableScanlators
import eu.kanade.domain.items.chapter.interactor.SetReadStatus
import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.manga.interactor.AddMangaTracks
import eu.kanade.domain.track.manga.interactor.RefreshMangaTracks
import eu.kanade.domain.track.manga.interactor.TrackChapter
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.presentation.util.StateViewModel
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.download.model.isPaused
import eu.kanade.tachiyomi.data.library.AutoUpdateCandidate
import eu.kanade.tachiyomi.data.library.EntryUpdateContext
import eu.kanade.tachiyomi.data.library.isEntryListOutdated
import eu.kanade.tachiyomi.data.track.EnhancedMangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.data.translation.TranslationState
import eu.kanade.tachiyomi.di.ViewModelIoDispatcher
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.manga.isLocalOrStub
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.ui.entries.common.EntryCategoryActions
import eu.kanade.tachiyomi.ui.entries.common.EntryDownloadProgress
import eu.kanade.tachiyomi.ui.entries.common.EntryDownloadStateUpdater
import eu.kanade.tachiyomi.ui.entries.common.EntryListGapSeparator
import eu.kanade.tachiyomi.ui.entries.common.EntrySelectionController
import eu.kanade.tachiyomi.ui.entries.common.EntryTrackingSummaryObserver
import eu.kanade.tachiyomi.ui.entries.common.SelectableEntryItem
import eu.kanade.tachiyomi.ui.entries.common.entryUpdateContextFlow
import eu.kanade.tachiyomi.ui.entries.common.fetchWindowFlow
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.util.chapter.StartAction
import eu.kanade.tachiyomi.util.chapter.startAction
import eu.kanade.tachiyomi.util.removeCovers
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.CheckboxState
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.entries.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.interactor.SetMangaChapterFlags
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaSourceLink
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.manga.repository.MangaSourceLinkRepository
import tachiyomi.domain.history.manga.interactor.GetMangaHistory
import tachiyomi.domain.items.chapter.interactor.SetMangaDefaultChapterFlags
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.model.NoChaptersException
import tachiyomi.domain.items.chapter.service.calculateChapterGap
import tachiyomi.domain.items.chapter.service.getChapterSort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.entries.manga.isLocal
import java.time.Clock

@Immutable
data class MangaSourceEndpointStatus(
    val unavailable: Boolean = false,
    val refreshFailed: Boolean = false,
)

@Immutable
data class MangaSourceStatus(
    val primary: MangaSourceEndpointStatus = MangaSourceEndpointStatus(),
    val linked: MangaSourceEndpointStatus = MangaSourceEndpointStatus(),
)

@AssistedInject
class MangaScreenModel(
    @Assisted private val context: Context,
    @Assisted private val lifecycle: Lifecycle,
    @Assisted private val mangaId: Long,
    @Assisted private val isFromSource: Boolean,
    private val libraryPreferences: LibraryPreferences,
    private val trackPreferences: TrackPreferences,
    readerPreferences: ReaderPreferences,
    private val trackerManager: TrackerManager,
    private val trackChapter: TrackChapter,
    private val downloadManager: MangaDownloadManager,
    private val downloadCache: MangaDownloadCache,
    private val getMangaAndChapters: GetMangaWithChapters,
    private val getDuplicateLibraryManga: GetDuplicateLibraryManga,
    private val getAvailableScanlators: GetAvailableScanlators,
    private val getExcludedScanlators: GetExcludedScanlators,
    private val setExcludedScanlators: SetExcludedScanlators,
    private val setMangaChapterFlags: SetMangaChapterFlags,
    private val setMangaDefaultChapterFlags: SetMangaDefaultChapterFlags,
    private val setReadStatus: SetReadStatus,
    private val updateChapter: UpdateChapter,
    private val updateManga: UpdateManga,
    private val getCategories: GetMangaCategories,
    private val getTracks: GetMangaTracks,
    private val addTracks: AddMangaTracks,
    private val setMangaCategories: SetMangaCategories,
    private val mangaRepository: MangaRepository,
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val entryRefresher: MangaEntryRefresher,
    private val translationManager: TranslationManager,
    private val fetchInterval: MangaFetchInterval,
    private val clock: Clock = Clock.systemDefaultZone(),
    @ViewModelIoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
    private val refreshTracksInteractor: RefreshMangaTracks,
    private val sourceManager: MangaSourceManager,
    private val mangaSourceLinkRepository: MangaSourceLinkRepository,
    private val sourcePreferences: SourcePreferences,
    private val getMangaHistory: GetMangaHistory,
) : StateViewModel<MangaScreenModel.State>(State.Loading) {

    @AssistedFactory
    @ManualViewModelAssistedFactoryKey
    @ContributesIntoMap(AppScope::class)
    fun interface Factory : ManualViewModelAssistedFactory {
        fun create(context: Context, lifecycle: Lifecycle, mangaId: Long, isFromSource: Boolean): MangaScreenModel
    }

    private val chapterListItemMapper = MangaChapterListItemMapper(downloadManager)

    private val successState: State.Success?
        get() = state.value as? State.Success

    val manga: Manga?
        get() = successState?.manga

    private val updateContext = entryUpdateContextFlow(
        restrictions = libraryPreferences.autoUpdateItemRestrictions().changes(),
        includedCategories = libraryPreferences.mangaUpdateCategories().changes(),
        excludedCategories = libraryPreferences.mangaUpdateCategoriesExclude().changes(),
        entryCategoryIds = getCategories.subscribe(mangaId).map { categories -> categories.map { it.id } },
        fetchWindow = fetchWindowFlow(fetchInterval::getWindow, clock, lifecycle),
    )
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val source: MangaSource?
        get() = successState?.source

    private val isFavorited: Boolean
        get() = manga?.favorite ?: false

    private val allChapters: List<ChapterList.Item>?
        get() = successState?.chapters

    private val filteredChapters: List<ChapterList.Item>?
        get() = successState?.processedChapters

    val chapterSwipeStartAction = libraryPreferences.swipeChapterEndAction().get()
    val chapterSwipeEndAction = libraryPreferences.swipeChapterStartAction().get()
    var autoTrackState = trackPreferences.autoUpdateTrackOnMarkRead().get()

    private val skipFiltered by readerPreferences.skipFiltered().asState(viewModelScope)

    private val selectedChapterIds: HashSet<Long> = HashSet()
    private val selectionController = EntrySelectionController(selectedChapterIds)
    private val linkedAutoRefreshAttemptedIds: MutableSet<Long> = mutableSetOf()

    internal var isFromChangeCategory: Boolean = false

    private var libraryChange: Job? = null

    internal val autoOpenTrack: Boolean
        get() = successState?.trackingAvailable == true && trackPreferences.trackOnAddingToLibrary().get()

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private inline fun updateSuccessState(func: (State.Success) -> State.Success) {
        mutableState.update {
            when (it) {
                State.Loading -> it
                is State.Success -> func(it)
            }
        }
    }

    init {
        viewModelScope.launch(ioDispatcher) {
            combine(
                getMangaAndChapters.subscribe(mangaId, applyScanlatorFilter = true).distinctUntilChanged(),
                downloadCache.changes,
                downloadManager.queueState,
                translationManager.translationStates,
            ) { mangaAndChapters, _, _, translationStates -> mangaAndChapters to translationStates }
                .flowWithLifecycle(lifecycle)
                .collectLatest { (mangaAndChapters, translationStates) ->
                    val (manga, chapters) = mangaAndChapters
                    updateSuccessState {
                        it.copy(
                            manga = manga,
                            chapters = chapterListItemMapper.map(
                                chapters = chapters,
                                manga = manga,
                                selectedChapterIds = selectedChapterIds,
                                translationStates = translationStates,
                            ),
                            translationSummary = translationSummaryFrom(translationStates, chapters),
                            linkedChapters = it.linkedManga?.let { linkedManga ->
                                chapterListItemMapper.map(
                                    chapters = it.linkedChapters.map { chapterItem -> chapterItem.chapter },
                                    manga = linkedManga,
                                    selectedChapterIds = selectedChapterIds,
                                    translationStates = translationStates,
                                )
                            }.orEmpty(),
                        )
                    }
                }
        }

        viewModelScope.launch(ioDispatcher) {
            getExcludedScanlators.subscribe(mangaId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { excludedScanlators ->
                    updateSuccessState {
                        it.copy(excludedScanlators = excludedScanlators)
                    }
                }
        }

        viewModelScope.launch(ioDispatcher) {
            getAvailableScanlators.subscribe(mangaId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { availableScanlators ->
                    updateSuccessState {
                        it.copy(availableScanlators = availableScanlators)
                    }
                }
        }

        viewModelScope.launch(ioDispatcher) {
            updateContext.collectLatest { context ->
                updateSuccessState { it.copy(updateContext = context) }
            }
        }

        observeDownloads()

        viewModelScope.launch(ioDispatcher) {
            combine(
                sourcePreferences.disabledMangaSources().changes(),
                sourcePreferences.enabledLanguages().changes(),
                sourceManager.sources,
            ) { disabledSources, enabledLanguages, registeredSources ->
                Triple(disabledSources, enabledLanguages, registeredSources.map { it.id }.toSet())
            }.flowWithLifecycle(lifecycle).collectLatest { (disabledSources, enabledLanguages, registeredSourceIds) ->
                updateSuccessState { current ->
                    current.copy(
                        sourceStatus = current.sourceStatus.copy(
                            primary = current.sourceStatus.primary.copy(
                                unavailable = current.source.isUnavailable(
                                    disabledSources,
                                    enabledLanguages,
                                    registeredSourceIds,
                                ),
                            ),
                            linked = current.linkedManga?.let {
                                current.sourceStatus.linked.copy(
                                    unavailable = sourceManager.getOrStub(it.source)
                                        .isUnavailable(disabledSources, enabledLanguages, registeredSourceIds),
                                )
                            } ?: MangaSourceEndpointStatus(),
                        ),
                    )
                }
            }
        }

        viewModelScope.launch(ioDispatcher) {
            val manga = getMangaAndChapters.awaitManga(mangaId)
            val chapters = getMangaAndChapters.awaitChapters(mangaId, applyScanlatorFilter = true)

            if (!manga.favorite) {
                setMangaDefaultChapterFlags.await(manga)
            }

            val needRefreshInfo = !manga.initialized
            val needRefreshChapter = chapters.isEmpty()
            val runningRefresh = entryRefresher.running(mangaId)
            val mangaSourceLink = mangaSourceLinkRepository.getForManga(mangaId)
            val linkedMangaId = mangaSourceLink?.let {
                if (it.firstMangaId == mangaId) it.secondMangaId else it.firstMangaId
            }
            val linkedManga = linkedMangaId?.let { getMangaAndChapters.awaitManga(it) }
            val source = sourceManager.getOrStub(manga.source)
            val linkedSource = linkedManga?.let { sourceManager.getOrStub(it.source) }
            val linkedChapters = if (linkedManga != null) {
                getMangaAndChapters.awaitChapters(linkedManga.id, applyScanlatorFilter = true)
            } else {
                emptyList()
            }

            // Show what we have earlier
            // One-shot initial render before any flow has emitted; the combine above corrects it
            // on its first emission, which is immediate because translationStates is a StateFlow.
            val translationStates = translationManager.translationStates.value
            mutableState.update {
                State.Success(
                    manga = manga,
                    source = source,
                    sourceStatus = MangaSourceStatus(
                        primary = MangaSourceEndpointStatus(unavailable = source.isUnavailable()),
                        linked = MangaSourceEndpointStatus(unavailable = linkedSource?.isUnavailable() ?: false),
                    ),
                    isFromSource = isFromSource,
                    chapters = chapterListItemMapper.map(
                        chapters = chapters,
                        manga = manga,
                        selectedChapterIds = selectedChapterIds,
                        translationStates = translationStates,
                    ),
                    mangaSourceLink = mangaSourceLink,
                    linkedManga = linkedManga,
                    linkedSourceName = linkedSource?.name,
                    linkedChapters = linkedManga?.let {
                        chapterListItemMapper.map(
                            chapters = linkedChapters,
                            manga = it,
                            selectedChapterIds = emptySet(),
                            translationStates = translationStates,
                        )
                    }.orEmpty(),
                    translationSummary = translationSummaryFrom(translationStates, chapters),
                    availableScanlators = getAvailableScanlators.await(mangaId),
                    excludedScanlators = getExcludedScanlators.await(mangaId),
                    isRefreshingData = needRefreshInfo || needRefreshChapter || runningRefresh != null,
                    dialog = null,
                    updateContext = updateContext.value,
                )
            }
            observeLinkedManga()
            // Start observe tracking since it only needs mangaId
            observeTrackers()

            // Some extensions reject a fetch that requests nothing.
            if (viewModelScope.isActive && (needRefreshInfo || needRefreshChapter)) {
                fetchMissingFromSource(
                    manga = manga,
                    link = mangaSourceLink,
                    fetchDetails = needRefreshInfo,
                    fetchChapters = needRefreshChapter,
                )
            }
            runningRefresh?.let { showRefresh(it, manga, mangaSourceLink) }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    private fun observeLinkedManga() {
        viewModelScope.launch(ioDispatcher) {
            mangaSourceLinkRepository.getForMangaAsFlow(mangaId).collectLatest { link ->
                if (link == null) {
                    updateSuccessState {
                        it.copy(
                            mangaSourceLink = null,
                            linkedManga = null,
                            linkedSourceName = null,
                            sourceStatus = it.sourceStatus.copy(linked = MangaSourceEndpointStatus()),
                            linkedChapters = emptyList(),
                        )
                    }
                    return@collectLatest
                }

                updateSuccessState {
                    it.copy(
                        mangaSourceLink = link,
                        linkedManga = null,
                        linkedSourceName = null,
                        sourceStatus = it.sourceStatus.copy(linked = MangaSourceEndpointStatus()),
                        linkedChapters = emptyList(),
                    )
                }
                val linkedMangaId = if (link.firstMangaId == mangaId) link.secondMangaId else link.firstMangaId
                try {
                    getMangaAndChapters.subscribe(
                        linkedMangaId,
                        applyScanlatorFilter = true,
                    ).collectLatest { (manga, chapters) ->
                        val source = sourceManager.getOrStub(manga.source)
                        val translationStates = translationManager.translationStates.value
                        updateSuccessState {
                            it.copy(
                                mangaSourceLink = link,
                                linkedManga = manga,
                                linkedSourceName = source.name,
                                sourceStatus = it.sourceStatus.copy(
                                    linked = it.sourceStatus.linked.copy(unavailable = source.isUnavailable()),
                                ),
                                linkedChapters = chapterListItemMapper.map(
                                    chapters = chapters,
                                    manga = manga,
                                    selectedChapterIds = selectedChapterIds,
                                    translationStates = translationStates,
                                ),
                            )
                        }
                        if (
                            !source.isUnavailable() && (chapters.isEmpty() || !manga.initialized) &&
                            linkedAutoRefreshAttemptedIds.add(manga.id)
                        ) {
                            viewModelScope.launch(ioDispatcher) {
                                val failure = entryRefresher.refresh(source, manga).await().exceptionOrNull()
                                recordRefreshOutcome(manga.id, link, failure)
                                if (failure != null && successState?.mangaSourceLink == link) {
                                    showLinkedSourceFailure(source.name)
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to observe linked manga $linkedMangaId" }
                }
            }
        }
    }

    fun fetchAllFromSource() {
        val state = successState?.takeUnless { it.isRefreshingData } ?: return
        val requestedLink = state.mangaSourceLink
        val entries = buildList {
            if (state.mangaSourceLink == null || !state.sourceUnavailable) add(state.manga to state.source)
            state.linkedManga?.let { linkedManga ->
                if (!state.linkedSourceUnavailable) add(linkedManga to sourceManager.getOrStub(linkedManga.source))
            }
        }
        if (entries.isEmpty()) return
        viewModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            entries.map { (entry, source) ->
                async {
                    val failure = entryRefresher.refresh(source, entry).await().exceptionOrNull()
                    recordRefreshOutcome(entry.id, requestedLink, failure)
                    if (failure != null && successState?.mangaSourceLink == requestedLink) {
                        if (entry.id == state.manga.id) {
                            showFetchError(failure)
                        } else {
                            showLinkedSourceFailure(source.name)
                        }
                    }
                }
            }.awaitAll()
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    private fun State.Success.firstReadableStartAction(): StartAction {
        val chapterItems = chapters + linkedChapters
        val chapterItemsById = chapterItems.associateBy { it.chapter.id }
        val sourceOrder = mapOf(
            (mangaSourceLink?.firstMangaId ?: manga.id) to 0,
            (mangaSourceLink?.secondMangaId ?: linkedManga?.id ?: manga.id) to 1,
        )
        val firstReadable = processedChapters
            .sortedWith(
                compareBy<ChapterList.Item>(
                    { if (it.chapter.isRecognizedNumber) 0 else 1 },
                    { if (it.chapter.isRecognizedNumber) it.chapter.chapterNumber else 0.0 },
                    { sourceOrder[it.chapter.mangaId] ?: Int.MAX_VALUE },
                    { it.chapter.sourceOrder },
                    { it.chapter.id },
                ),
            )
            .asSequence()
            .flatMap { item ->
                val versions = item.versions.ifEmpty {
                    listOf(
                        MangaChapterVersion(
                            manga = item.ownerManga ?: manga,
                            chapter = item.chapter,
                            sourceName = item.sourceName ?: source.name,
                        ),
                    )
                }
                versions.asSequence()
            }
            .firstOrNull { version ->
                if (version.chapter.read) return@firstOrNull false
                val unavailable = isOwnerUnavailable(version.manga.id)
                !unavailable || chapterItemsById[version.chapter.id]?.isDownloaded == true
            } ?: return StartAction.None

        return if (firstReadable.chapter.lastPageRead > 0L) {
            StartAction.Resume(firstReadable.chapter)
        } else {
            StartAction.FirstStart(firstReadable.chapter)
        }
    }

    private fun State.Success.isOwnerUnavailable(ownerMangaId: Long): Boolean = when (ownerMangaId) {
        manga.id -> sourceUnavailable || sourceRefreshFailed
        linkedManga?.id -> linkedSourceUnavailable || linkedSourceRefreshFailed
        else -> true
    }

    private fun recordRefreshOutcome(ownerMangaId: Long, link: MangaSourceLink?, failure: Throwable?) {
        updateSuccessState { state ->
            if (state.mangaSourceLink != link) {
                state
            } else {
                state.updateEndpointStatus(ownerMangaId) {
                    it.copy(refreshFailed = failure != null)
                }
            }
        }
    }

    private fun State.Success.updateEndpointStatus(
        ownerMangaId: Long,
        update: (MangaSourceEndpointStatus) -> MangaSourceEndpointStatus,
    ): State.Success = when (ownerMangaId) {
        manga.id -> copy(sourceStatus = sourceStatus.copy(primary = update(sourceStatus.primary)))
        linkedManga?.id -> copy(sourceStatus = sourceStatus.copy(linked = update(sourceStatus.linked)))
        else -> this
    }

    suspend fun continueReadingDecision(): ContinueReadingDecision {
        val current = successState ?: return ContinueReadingDecision.None
        val partner = current.linkedManga ?: return ContinueReadingDecision.Launch(
            current.chapters.startAction(current.manga),
        )
        val latestHistory = (getMangaHistory.await(current.manga.id) + getMangaHistory.await(partner.id))
            .filter { (it.readAt?.time ?: 0L) > 0L }
            .maxWithOrNull(compareBy({ it.readAt!!.time }, { it.chapterId }))
        val latestChapterId = latestHistory?.chapterId
        val chapterItems = current.chapters + current.linkedChapters
        val latestItem = latestChapterId?.let { id -> chapterItems.firstOrNull { it.chapter.id == id } }
        if (latestItem == null) {
            return ContinueReadingDecision.Launch(current.firstReadableStartAction())
        }

        val ownerUnavailable = current.isOwnerUnavailable(latestItem.chapter.mangaId)
        if (!ownerUnavailable || latestItem.isDownloaded) {
            return ContinueReadingDecision.Launch(StartAction.Resume(latestItem.chapter))
        }

        val versions = current.processedChapters.flatMap { item ->
            if (item.versions.isNotEmpty()) {
                item.versions
            } else {
                listOf(
                    MangaChapterVersion(
                        manga = item.ownerManga ?: current.manga,
                        chapter = item.chapter,
                        sourceName = item.sourceName ?: current.source.name,
                    ),
                )
            }
        }.filter { version ->
            val unavailable = current.isOwnerUnavailable(version.manga.id)
            val savedItem = chapterItems.firstOrNull { it.chapter.id == version.chapter.id }
            !unavailable || savedItem?.isDownloaded == true
        }
        return ContinueReadingDecision.ChooseChapter(
            unavailableSourceName = if (latestItem.chapter.mangaId == current.manga.id) {
                current.source.name
            } else {
                current.linkedSourceName.orEmpty()
            },
            versions = versions,
        )
    }

    fun unlinkSources(link: MangaSourceLink) {
        viewModelScope.launch(ioDispatcher) {
            val unlinked = mangaSourceLinkRepository.unlink(link.firstMangaId, link.secondMangaId)
            if (!unlinked) return@launch
            updateSuccessState {
                if (it.mangaSourceLink != link) {
                    it
                } else {
                    it.copy(
                        mangaSourceLink = null,
                        linkedManga = null,
                        linkedSourceName = null,
                        sourceStatus = it.sourceStatus.copy(linked = MangaSourceEndpointStatus()),
                        linkedChapters = emptyList(),
                    )
                }
            }
        }
    }

    private suspend fun showRefresh(refresh: Deferred<Result<Unit>>, manga: Manga, link: MangaSourceLink?) {
        updateSuccessState { it.copy(isRefreshingData = true) }
        val failure = refresh.await().exceptionOrNull()
        recordRefreshOutcome(manga.id, link, failure)
        failure?.let(::showFetchError)
        updateSuccessState { it.copy(isRefreshingData = false) }
    }

    // Manga info - start

    private suspend fun fetchMissingFromSource(
        manga: Manga,
        link: MangaSourceLink?,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) {
        val source = successState?.source ?: return
        var failure: Throwable? = null
        try {
            withContext(ioDispatcher) {
                updateMangaFromRemote(
                    source = source,
                    manga = manga,
                    fetchDetails = fetchDetails,
                    fetchChapters = fetchChapters,
                )
                    .getOrThrow()
            }
        } catch (_: CancellationException) {
            return
        } catch (e: Exception) {
            failure = e
            showFetchError(e)
        }
        recordRefreshOutcome(manga.id, link, failure)
    }

    private fun MangaSource.isUnavailable(
        disabledSources: Set<String>? = null,
        enabledLanguages: Set<String>? = null,
        registeredSourceIds: Set<Long>? = null,
    ): Boolean {
        if (this is StubMangaSource) return true
        if (isLocalOrStub()) return false

        val disabled = disabledSources ?: sourcePreferences.disabledMangaSources().get()
        val languages = enabledLanguages ?: sourcePreferences.enabledLanguages().get()
        val registered = registeredSourceIds ?: sourceManager.getAll().mapTo(mutableSetOf()) { it.id }
        return id.toString() in disabled || lang !in languages || id !in registered
    }

    private fun State.Success.originalMangaForChapter(chapter: Chapter): Manga? = when (chapter.mangaId) {
        manga.id -> manga
        linkedManga?.id -> linkedManga
        else -> null
    }

    private fun showLinkedSourceFailure(sourceName: String) {
        viewModelScope.launch {
            snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.link_manga_source_refresh_failed, sourceName),
            )
        }
    }

    private fun showFetchError(e: Throwable) {
        val message = if (e is NoChaptersException) {
            context.stringResource(MR.strings.no_chapters_error)
        } else {
            logcat(LogPriority.ERROR, e)
            with(context) { e.formattedMessage }
        }

        viewModelScope.launch { showSourceFailure(message) }
    }

    private val recoveryChannel = Channel<Long>(Channel.BUFFERED)
    val recoveryRequests: Flow<Long> = recoveryChannel.receiveAsFlow()

    private suspend fun showSourceFailure(message: String) {
        val entry = successState?.manga
        if (entry?.favorite != true) {
            snackbarHostState.showSnackbar(message = message)
            return
        }
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = context.stringResource(MR.strings.recovery_title),
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) recoveryChannel.send(entry.source)
    }

    fun toggleFavorite() {
        toggleFavorite(
            onRemoved = {
                viewModelScope.launch {
                    if (!hasDownloads()) return@launch
                    val result = snackbarHostState.showSnackbar(
                        message = context.stringResource(MR.strings.delete_downloads_for_manga),
                        actionLabel = context.stringResource(MR.strings.action_delete),
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        deleteDownloads()
                    }
                }
            },
        )
    }

    /**
     * Update favorite status of manga, (removes / adds) manga (to / from) library.
     */
    fun toggleFavorite(
        onRemoved: () -> Unit,
        checkDuplicate: Boolean = true,
    ) {
        val state = successState ?: return
        if (libraryChange?.isActive == true) return
        libraryChange = viewModelScope.launch(ioDispatcher) {
            val manga = state.manga
            val retry = { toggleFavorite(onRemoved, checkDuplicate) }

            if (isFavorited) {
                // Remove from library
                if (!updateManga.awaitUpdateFavorite(manga.id, false)) {
                    showLibraryChangeFailure(adding = false, retry)
                    return@launch
                }
                confirmLibraryChange(favorite = false)
                // Remove covers and update last modified in db
                if (manga.removeCovers() != manga) {
                    updateManga.awaitUpdateCoverLastModified(manga.id)
                }
                withUIContext { onRemoved() }
            } else {
                // Add to library
                // First, check if duplicate exists if callback is provided
                if (checkDuplicate) {
                    val duplicate = getDuplicateLibraryManga.await(manga).firstOrNull()
                    if (duplicate != null) {
                        updateSuccessState { it.copy(dialog = Dialog.DuplicateManga(manga, duplicate)) }
                        return@launch
                    }
                }

                // Now check if user previously set categories, when available
                val categories = getCategories()
                val defaultCategoryId = libraryPreferences.defaultMangaCategory().get().toLong()
                val defaultCategory = categories.find { it.id == defaultCategoryId }
                if (defaultCategory == null && defaultCategoryId != 0L && categories.isNotEmpty()) {
                    isFromChangeCategory = true
                    showChangeCategoryDialog()
                } else {
                    if (!updateManga.awaitUpdateFavorite(manga.id, true)) {
                        showLibraryChangeFailure(adding = true, retry)
                        return@launch
                    }
                    confirmLibraryChange(favorite = true)
                    moveMangaToCategory(defaultCategory)
                }

                // Finally match with enhanced tracking when available
                viewModelScope.launch(ioDispatcher) {
                    addTracks.bindEnhancedTrackers(manga, state.source)
                    if (autoOpenTrack) {
                        showTrackDialog()
                    }
                }
            }
        }
    }

    fun showChangeCategoryDialog() {
        val manga = successState?.manga ?: return
        viewModelScope.launch {
            val categories = getCategories()
            val selection = getMangaCategoryIds(manga)
            updateSuccessState { successState ->
                successState.copy(
                    dialog = Dialog.ChangeCategory(
                        manga = manga,
                        initialSelection = EntryCategoryActions.buildInitialSelection(categories, selection),
                    ),
                )
            }
        }
    }

    fun showSetMangaFetchIntervalDialog() {
        val manga = successState?.manga ?: return
        updateSuccessState {
            it.copy(dialog = Dialog.SetMangaFetchInterval(manga))
        }
    }

    fun setFetchInterval(manga: Manga, interval: Int) {
        viewModelScope.launchIO {
            if (
                updateManga.awaitUpdateFetchInterval(
                    // Custom intervals are negative
                    manga.copy(fetchInterval = -interval),
                )
            ) {
                val updatedManga = mangaRepository.getMangaById(manga.id)
                updateSuccessState { it.copy(manga = updatedManga) }
            }
        }
    }

    /**
     * Returns true if the manga has any downloads.
     */
    private fun hasDownloads(): Boolean {
        val manga = successState?.manga ?: return false
        return downloadManager.getDownloadCount(manga) > 0
    }

    /**
     * Deletes all the downloads for the manga.
     */
    private fun deleteDownloads() {
        val state = successState ?: return
        downloadManager.deleteManga(state.manga, state.source)
    }

    /**
     * Get user categories.
     *
     * @return List of categories, not including the default category
     */
    suspend fun getCategories(): List<Category> {
        return getCategories.await().filterNot { it.isSystemCategory }
    }

    /**
     * Gets the category id's the manga is in, if the manga is not in a category, returns the default id.
     *
     * @param manga the manga to get categories from.
     * @return Array of category ids the manga is in, if none returns default id
     */
    private suspend fun getMangaCategoryIds(manga: Manga): List<Long> {
        return getCategories.await(manga.id)
            .map { it.id }
    }

    fun moveMangaToCategoriesAndAddToLibrary(manga: Manga, categories: List<Long>) {
        moveMangaToCategory(categories)
        if (manga.favorite || libraryChange?.isActive == true) return

        libraryChange = viewModelScope.launch(ioDispatcher) {
            if (updateManga.awaitUpdateFavorite(manga.id, true)) {
                confirmLibraryChange(favorite = true)
            } else {
                showLibraryChangeFailure(adding = true) { moveMangaToCategoriesAndAddToLibrary(manga, categories) }
            }
        }
    }

    private fun confirmLibraryChange(favorite: Boolean) {
        updateSuccessState {
            it.copy(
                manga = it.manga.copy(favorite = favorite),
                libraryConfirmations = it.libraryConfirmations + 1,
            )
        }
    }

    private fun showLibraryChangeFailure(adding: Boolean, retry: () -> Unit) {
        viewModelScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(
                    if (adding) MR.strings.library_add_failed else MR.strings.library_remove_failed,
                ),
                actionLabel = context.stringResource(MR.strings.action_retry),
                withDismissAction = true,
            )
            if (result == SnackbarResult.ActionPerformed && isFavorited != adding) retry()
        }
    }

    /**
     * Move the given manga to categories.
     *
     * @param categories the selected categories.
     */
    private fun moveMangaToCategories(categories: List<Category>) {
        val categoryIds = categories.map { it.id }
        moveMangaToCategory(categoryIds)
    }

    private fun moveMangaToCategory(categoryIds: List<Long>) {
        viewModelScope.launchIO {
            setMangaCategories.await(mangaId, categoryIds)
        }
    }

    /**
     * Move the given manga to the category.
     *
     * @param category the selected category, or null for default category.
     */
    private fun moveMangaToCategory(category: Category?) {
        moveMangaToCategories(listOfNotNull(category))
    }

    // Manga info - end

    // Chapters list - start

    private fun observeDownloads() {
        viewModelScope.launch(ioDispatcher) {
            downloadManager.statusFlow()
                .filter { download ->
                    val state = successState
                    download.manga.id == state?.manga?.id || download.manga.id == state?.linkedManga?.id
                }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: MangaDownload) {
        updateSuccessState { state ->
            val updateItems = { items: List<ChapterList.Item> ->
                EntryDownloadStateUpdater.update(
                    items = items,
                    entryId = download.chapter.id,
                    idSelector = { it.id },
                    updateItem = {
                        it.copy(
                            downloadState = download.status,
                            downloadPaused = download.displayStatus.isPaused,
                            downloadProgress = download.progress,
                            downloadProgressSource = EntryDownloadProgress(download.progressFlow) { download.progress },
                        )
                    },
                )
            }
            when (download.manga.id) {
                state.manga.id -> state.copy(chapters = updateItems(state.chapters))
                state.linkedManga?.id -> state.copy(linkedChapters = updateItems(state.linkedChapters))
                else -> state
            }
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    fun chapterSwipe(chapterItem: ChapterList.Item, swipeAction: LibraryPreferences.ChapterSwipeAction) {
        viewModelScope.launch {
            executeChapterSwipeAction(chapterItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.ChapterSwipeAction.Disabled]
     */
    private fun executeChapterSwipeAction(
        chapterItem: ChapterList.Item,
        swipeAction: LibraryPreferences.ChapterSwipeAction,
    ) {
        val chapter = chapterItem.chapter
        when (swipeAction) {
            LibraryPreferences.ChapterSwipeAction.ToggleRead -> {
                markChaptersRead(listOf(chapter), !chapter.read)
            }
            LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> {
                bookmarkChapters(listOf(chapter), !chapter.bookmark)
            }
            LibraryPreferences.ChapterSwipeAction.Download -> {
                val downloadAction: ChapterDownloadAction = when (chapterItem.downloadState) {
                    MangaDownload.State.ERROR,
                    MangaDownload.State.NOT_DOWNLOADED,
                    -> ChapterDownloadAction.START_NOW
                    MangaDownload.State.QUEUE,
                    MangaDownload.State.DOWNLOADING,
                    -> ChapterDownloadAction.CANCEL
                    MangaDownload.State.DOWNLOADED -> ChapterDownloadAction.DELETE
                }
                runChapterDownloadActions(
                    items = listOf(chapterItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.ChapterSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    private fun getUnreadChapters(): List<Chapter> {
        val chapterItems = if (skipFiltered) filteredChapters.orEmpty() else allChapters.orEmpty()
        return chapterItems
            .filter { (chapter, dlStatus) -> !chapter.read && dlStatus == MangaDownload.State.NOT_DOWNLOADED }
            .map { it.chapter }
    }

    private fun getUnreadChaptersSorted(): List<Chapter> {
        val manga = successState?.manga ?: return emptyList()
        val chaptersSorted = getUnreadChapters().sortedWith(getChapterSort(manga))
        return if (manga.sortDescending()) chaptersSorted.reversed() else chaptersSorted
    }

    private fun startDownload(
        chapters: List<Chapter>,
        startNow: Boolean,
    ) {
        val successState = successState ?: return

        viewModelScope.launchNonCancellable {
            if (startNow) {
                val chapterId = chapters.singleOrNull()?.id ?: return@launchNonCancellable
                downloadManager.startDownloadNow(chapterId)
            } else {
                downloadChapters(chapters)
            }

            if (!isFavorited && !successState.hasPromptedToAddBefore) {
                updateSuccessState { state ->
                    state.copy(hasPromptedToAddBefore = true)
                }
                val result = snackbarHostState.showSnackbar(
                    message = context.stringResource(AYMR.strings.snack_add_to_manga_library),
                    actionLabel = context.stringResource(MR.strings.action_add),
                    withDismissAction = true,
                )
                if (result == SnackbarResult.ActionPerformed && !isFavorited) {
                    withUIContext { toggleFavorite() }
                }
            }
        }
    }

    fun runChapterDownloadActions(
        items: List<ChapterList.Item>,
        action: ChapterDownloadAction,
    ) {
        val state = successState ?: return
        val eligibleItems = items.filter { item ->
            val owner = state.originalMangaForChapter(item.chapter) ?: return@filter false
            !sourceManager.getOrStub(owner.source).isLocalOrStub()
        }
        if (eligibleItems.isEmpty()) return
        when (action) {
            ChapterDownloadAction.START -> {
                startDownload(eligibleItems.map { it.chapter }, false)
                if (eligibleItems.any { it.downloadState == MangaDownload.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            ChapterDownloadAction.START_NOW -> {
                val chapter = eligibleItems.singleOrNull()?.chapter ?: return
                startDownload(listOf(chapter), true)
            }
            ChapterDownloadAction.CANCEL -> {
                val chapterId = eligibleItems.singleOrNull()?.id ?: return
                cancelDownload(chapterId)
            }
            ChapterDownloadAction.DELETE -> {
                deleteChapters(eligibleItems.map { it.chapter })
            }
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        val chaptersToDownload = when (action) {
            DownloadAction.NEXT_1_ITEM -> getUnreadChaptersSorted().take(1)
            DownloadAction.NEXT_5_ITEMS -> getUnreadChaptersSorted().take(5)
            DownloadAction.NEXT_10_ITEMS -> getUnreadChaptersSorted().take(10)
            DownloadAction.NEXT_25_ITEMS -> getUnreadChaptersSorted().take(25)

            DownloadAction.UNVIEWED_ITEMS -> getUnreadChapters()
        }
        if (chaptersToDownload.isNotEmpty()) {
            startDownload(chaptersToDownload, false)
        }
    }

    private fun cancelDownload(chapterId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(chapterId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = MangaDownload.State.NOT_DOWNLOADED })
    }

    // Translation

    fun translateChapter(chapter: Chapter) {
        val state = successState ?: return
        val manga = state.originalMangaForChapter(chapter) ?: return
        val source = sourceManager.getOrStub(manga.source)
        translationManager.translateChapter(manga, chapter, source)
    }

    fun deleteTranslation(chapter: Chapter) {
        val state = successState ?: return
        val manga = state.originalMangaForChapter(chapter) ?: return
        val source = sourceManager.getOrStub(manga.source)
        translationManager.deleteTranslation(chapter, manga.title, source)
    }

    fun markPreviousChapterRead(pointer: Chapter) {
        val manga = successState?.manga ?: return
        val chapters = filteredChapters.orEmpty().map { it.chapter }
        val prevChapters = if (manga.sortDescending()) chapters.asReversed() else chapters
        val pointerPos = prevChapters.indexOf(pointer)
        if (pointerPos != -1) markChaptersRead(prevChapters.take(pointerPos), true)
    }

    /**
     * Mark the selected chapter list as read/unread.
     * @param chapters the list of selected chapters.
     * @param read whether to mark chapters as read or unread.
     */
    fun markChaptersRead(chapters: List<Chapter>, read: Boolean) {
        toggleAllSelection(false)
        if (chapters.isEmpty()) return
        viewModelScope.launchIO {
            setReadStatus.await(
                read = read,
                chapters = chapters.toTypedArray(),
            )

            if (!read || successState?.hasLoggedInTrackers == false || autoTrackState == AutoTrackState.NEVER) {
                return@launchIO
            }

            val state = successState ?: return@launchIO
            chapters
                .mapNotNull { chapter -> state.originalMangaForChapter(chapter)?.let { it to chapter } }
                .groupBy({ it.first }, { it.second })
                .forEach { (owner, ownerChapters) ->
                    refreshTrackers(owner.id)

                    val tracks = getTracks.await(owner.id)
                    val maxChapterNumber = ownerChapters.maxOf { it.chapterNumber }
                    val shouldPromptTrackingUpdate = tracks.any { track -> maxChapterNumber > track.lastChapterRead }
                    if (!shouldPromptTrackingUpdate) return@forEach

                    if (autoTrackState == AutoTrackState.ALWAYS) {
                        trackChapter.await(context, owner.id, maxChapterNumber)
                        withUIContext {
                            context.toast(
                                context.stringResource(
                                    AYMR.strings.trackers_updated_summary_manga,
                                    maxChapterNumber.toInt(),
                                ),
                            )
                        }
                        return@forEach
                    }

                    val result = snackbarHostState.showSnackbar(
                        message = context.stringResource(MR.strings.confirm_tracker_update, maxChapterNumber.toInt()),
                        actionLabel = context.stringResource(MR.strings.action_ok),
                        duration = SnackbarDuration.Short,
                        withDismissAction = true,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        trackChapter.await(context, owner.id, maxChapterNumber)
                    }
                }
        }
    }

    private suspend fun refreshTrackers(
        ownerMangaId: Long = mangaId,
        refreshTracks: RefreshMangaTracks = refreshTracksInteractor,
    ) {
        refreshTracks.await(ownerMangaId)
            .filter { it.first != null }
            .forEach { (track, e) ->
                logcat(LogPriority.ERROR, e) {
                    "Failed to refresh track data mangaId=$ownerMangaId for service ${track!!.id}"
                }
                withUIContext {
                    context.toast(
                        context.stringResource(
                            MR.strings.track_error,
                            track!!.name,
                            e.message ?: "",
                        ),
                    )
                }
            }
    }

    /**
     * Downloads the given list of chapters with the manager.
     * @param chapters the list of chapters to download.
     */
    private fun downloadChapters(chapters: List<Chapter>) {
        val state = successState ?: return
        chapters
            .mapNotNull { chapter -> state.originalMangaForChapter(chapter)?.let { it to chapter } }
            .groupBy({ it.first }, { it.second })
            .forEach { (owner, ownerChapters) -> downloadManager.downloadChapters(owner, ownerChapters) }
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of chapters.
     * @param chapters the list of chapters to bookmark.
     */
    fun bookmarkChapters(chapters: List<Chapter>, bookmarked: Boolean) {
        viewModelScope.launchIO {
            chapters
                .filterNot { it.bookmark == bookmarked }
                .map { ChapterUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateChapter.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Deletes the given list of chapter.
     *
     * @param chapters the list of chapters to delete.
     */
    fun deleteChapters(chapters: List<Chapter>) {
        viewModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    chapters
                        .mapNotNull { chapter -> state.originalMangaForChapter(chapter)?.let { it to chapter } }
                        .groupBy({ it.first }, { it.second })
                        .forEach { (owner, ownerChapters) ->
                            downloadManager.deleteChapters(
                                ownerChapters,
                                owner,
                                sourceManager.getOrStub(owner.source),
                            )
                        }
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    /**
     * Sets the read filter and requests an UI update.
     * @param state whether to display only unread chapters or all chapters.
     */
    fun setUnreadFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_UNREAD
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_READ
        }
        viewModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetUnreadFilter(manga, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded chapters or all chapters.
     */
    fun setDownloadedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_DOWNLOADED
        }

        viewModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDownloadedFilter(manga, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked chapters or all chapters.
     */
    fun setBookmarkedFilter(state: TriState) {
        val manga = successState?.manga ?: return

        val flag = when (state) {
            TriState.DISABLED -> Manga.SHOW_ALL
            TriState.ENABLED_IS -> Manga.CHAPTER_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Manga.CHAPTER_SHOW_NOT_BOOKMARKED
        }

        viewModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetBookmarkFilter(manga, flag)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val manga = successState?.manga ?: return

        viewModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetDisplayMode(manga, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val manga = successState?.manga ?: return

        viewModelScope.launchNonCancellable {
            setMangaChapterFlags.awaitSetSortingModeOrFlipOrder(manga, sort)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val manga = successState?.manga ?: return
        viewModelScope.launchNonCancellable {
            libraryPreferences.setChapterSettingsDefault(manga)
            if (applyToExisting) {
                setMangaDefaultChapterFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(
                message = context.stringResource(MR.strings.chapter_settings_updated),
            )
        }
    }

    fun resetToDefaultSettings() {
        val manga = successState?.manga ?: return
        viewModelScope.launchNonCancellable {
            setMangaDefaultChapterFlags.await(manga)
        }
    }

    fun toggleSelection(
        item: ChapterList.Item,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { state ->
            val allItems = state.chapters + state.linkedChapters
            val newItems = selectionController.toggleSelection(
                visibleItems = state.processedChapters,
                allItems = allItems,
                itemId = item.id,
                selected = selected,
                userSelected = userSelected,
                fromLongPress = fromLongPress,
                updateSelection = { value, selectedValue -> value.copy(selected = selectedValue) },
            )
            if (newItems === allItems) return@updateSuccessState state
            state.withChapterSelection(newItems)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { state ->
            val visibleItems = if (state.linkedManga == null) state.chapters else state.processedChapters
            val newItems = selectionController.toggleAllSelection(
                items = visibleItems,
                selected = selected,
                updateSelection = { value, selectedValue -> value.copy(selected = selectedValue) },
            )
            state.withChapterSelection(newItems)
        }
    }

    fun invertSelection() {
        updateSuccessState { state ->
            val visibleItems = if (state.linkedManga == null) state.chapters else state.processedChapters
            val newItems = selectionController.invertSelection(
                items = visibleItems,
                updateSelection = { value, selectedValue -> value.copy(selected = selectedValue) },
            )
            state.withChapterSelection(newItems)
        }
    }

    private fun State.Success.withChapterSelection(visibleItems: List<ChapterList.Item>): State.Success {
        val selectedIds = visibleItems.asSequence().filter { it.selected }.mapTo(HashSet()) { it.id }
        selectedChapterIds.clear()
        selectedChapterIds.addAll(selectedIds)
        return copy(
            chapters = chapters.map { it.copy(selected = it.id in selectedIds) },
            linkedChapters = linkedChapters.map { it.copy(selected = it.id in selectedIds) },
        )
    }

    // Chapters list - end

    // Track sheet - start

    private fun observeTrackers() {
        val manga = successState?.manga ?: return

        viewModelScope.launch(ioDispatcher) {
            combine(
                getTracks.subscribe(manga.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { mangaTracks, loggedInTrackers ->
                EntryTrackingSummaryObserver.summarize(
                    tracks = mangaTracks,
                    loggedInTrackers = loggedInTrackers,
                    trackId = { it.trackerId },
                    isTrackerSupported = { (it as? EnhancedMangaTracker)?.accept(source!!) ?: true },
                )
            }
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { trackingSummary ->
                    updateSuccessState {
                        it.copy(
                            trackingCount = trackingSummary.trackingCount,
                            hasLoggedInTrackers = trackingSummary.hasLoggedInTrackers,
                        )
                    }
                }
        }
    }

    // Track sheet - end

    sealed interface Dialog {
        data class ChangeCategory(
            val manga: Manga,
            val initialSelection: ImmutableList<CheckboxState<Category>>,
        ) : Dialog
        data class DeleteChapters(val chapters: List<Chapter>) : Dialog
        data class DuplicateManga(val manga: Manga, val duplicate: Manga) : Dialog
        data class Migrate(val newManga: Manga, val oldManga: Manga) : Dialog
        data class SetMangaFetchInterval(val manga: Manga) : Dialog
        data object SettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object FullCover : Dialog
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun showDeleteChapterDialog(chapters: List<Chapter>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteChapters(chapters)) }
    }

    fun showSettingsDialog() {
        updateSuccessState { it.copy(dialog = Dialog.SettingsSheet) }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    fun showCoverDialog() {
        updateSuccessState { it.copy(dialog = Dialog.FullCover) }
    }

    fun showMigrateDialog(duplicate: Manga) {
        val manga = successState?.manga ?: return
        updateSuccessState { it.copy(dialog = Dialog.Migrate(newManga = manga, oldManga = duplicate)) }
    }

    fun setExcludedScanlators(excludedScanlators: Set<String>) {
        viewModelScope.launchIO {
            setExcludedScanlators.await(mangaId, excludedScanlators)
        }
    }

    sealed interface ContinueReadingDecision {
        data class Launch(val action: StartAction) : ContinueReadingDecision

        data class ChooseChapter(
            val unavailableSourceName: String,
            val versions: List<MangaChapterVersion>,
        ) : ContinueReadingDecision

        data object None : ContinueReadingDecision
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val manga: Manga,
            val source: MangaSource,
            val isFromSource: Boolean,
            val chapters: List<ChapterList.Item>,
            val availableScanlators: Set<String>,
            val excludedScanlators: Set<String>,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val hasPromptedToAddBefore: Boolean = false,
            val libraryConfirmations: Int = 0,
            val translationSummary: TranslationSummary? = null,
            val updateContext: EntryUpdateContext? = null,
            val mangaSourceLink: MangaSourceLink? = null,
            val linkedManga: Manga? = null,
            val linkedSourceName: String? = null,
            val sourceStatus: MangaSourceStatus = MangaSourceStatus(),
            val linkedChapters: List<ChapterList.Item> = emptyList(),
        ) : State {
            val sourceUnavailable: Boolean get() = sourceStatus.primary.unavailable
            val sourceRefreshFailed: Boolean get() = sourceStatus.primary.refreshFailed
            val linkedSourceUnavailable: Boolean get() = sourceStatus.linked.unavailable
            val linkedSourceRefreshFailed: Boolean get() = sourceStatus.linked.refreshFailed

            val processedChapters: List<ChapterList.Item> = if (linkedManga == null) {
                chapters.applyFilters(manga).toList()
            } else {
                val first = chapters.applyFilters(manga).toList()
                val second = linkedChapters.applyFilters(linkedManga).toList()
                val itemByChapter = (first.map { manga to it } + second.map { linkedManga to it })
                    .associate { (owner, item) -> (owner.id to item.chapter.id) to item }
                MangaChapterSequence.project(
                    firstManga = manga,
                    firstChapters = first.map { it.chapter },
                    firstSourceName = source.name,
                    secondManga = linkedManga,
                    secondChapters = second.map { it.chapter },
                    secondSourceName = linkedSourceName.orEmpty(),
                    link = mangaSourceLink ?: MangaSourceLink(manga.id, linkedManga.id),
                    descending = manga.sortDescending(),
                ).map { group ->
                    val representative = group.representative
                    itemByChapter.getValue(representative.manga.id to representative.chapter.id).copy(
                        ownerManga = representative.manga,
                        sourceName = representative.sourceName,
                        versions = group.versions,
                    )
                }
            }

            val isAnySelected by lazy {
                (chapters + linkedChapters).fastAny { it.selected }
            }

            val isListOutdated by lazy {
                val context = updateContext ?: return@lazy false
                !isRefreshingData && isEntryListOutdated(autoUpdateCandidate(), manga.favorite, context)
            }

            private fun autoUpdateCandidate() = AutoUpdateCandidate(
                alwaysUpdate = manga.updateStrategy == UpdateStrategy.ALWAYS_UPDATE,
                isCompleted = manga.status == SManga.COMPLETED.toLong(),
                hasUnviewed = chapters.fastAny { !it.chapter.read },
                hasStarted = chapters.fastAny { it.chapter.read },
                totalCount = chapters.size.toLong(),
                nextUpdate = manga.nextUpdate,
            )

            val chapterListItems = EntryListGapSeparator.withMissingCount(
                items = processedChapters,
                sortDescending = manga.sortDescending(),
                itemId = { it.id },
                itemNumber = { it.chapter.chapterNumber },
                calculateGap = { higher, lower -> calculateChapterGap(higher.chapter, lower.chapter) },
                mapItem = { it },
                mapMissing = { id, count -> ChapterList.MissingCount(id = id, count = count) },
            )

            val scanlatorFilterActive: Boolean
                get() = excludedScanlators.intersect(availableScanlators).isNotEmpty()

            val filterActive: Boolean
                get() = scanlatorFilterActive || manga.chaptersFiltered()

            val trackingAvailable: Boolean
                get() = trackingCount > 0

            /**
             * Applies the view filters to the list of chapters obtained from the database.
             * @return an observable of the list of chapters filtered and sorted.
             */
            private fun List<ChapterList.Item>.applyFilters(manga: Manga): Sequence<ChapterList.Item> {
                val isLocalManga = manga.isLocal()
                val unreadFilter = manga.unreadFilter
                val downloadedFilter = manga.downloadedFilter
                val bookmarkedFilter = manga.bookmarkedFilter
                val comparator = getChapterSort(manga)
                return asSequence()
                    .filter { (chapter) -> applyFilter(unreadFilter) { !chapter.read } }
                    .filter { (chapter) -> applyFilter(bookmarkedFilter) { chapter.bookmark } }
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalManga } }
                    .sortedWith { (chapter1), (chapter2) ->
                        comparator.invoke(
                            chapter1,
                            chapter2,
                        )
                    }
            }
        }
    }
}

@Immutable
sealed class ChapterList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : ChapterList()

    @Immutable
    data class Item(
        val chapter: Chapter,
        val downloadState: MangaDownload.State,
        val downloadProgress: Int,
        override val selected: Boolean = false,
        val translationState: TranslationState = TranslationState.Idle,
        val downloadProgressSource: EntryDownloadProgress? = null,
        val downloadPaused: Boolean = false,
        val ownerManga: Manga? = null,
        val sourceName: String? = null,
        val versions: List<MangaChapterVersion> = emptyList(),
    ) : ChapterList(), SelectableEntryItem {
        override val id: Long
            get() = chapter.id
        val isDownloaded = downloadState == MangaDownload.State.DOWNLOADED
    }
}
