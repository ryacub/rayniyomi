package eu.kanade.tachiyomi.ui.entries.manga

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import eu.kanade.domain.source.manga.model.RemoteMangaUpdate
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks

@OptIn(ExperimentalCoroutinesApi::class)
class MangaScreenModelSourceFetchTest {

    private val vt = VirtualTime()
    private val updateMangaFromRemote = mockk<UpdateMangaFromRemote>()
    private val filterChaptersForDownload = mockk<FilterChaptersForDownload> {
        coEvery { await(any(), any()) } answers { secondArg() }
    }
    private val downloadManager = mockk<MangaDownloadManager>(relaxed = true) {
        every { queueState } returns MutableStateFlow(emptyList())
        every { statusFlow() } returns emptyFlow()
        every { progressFlow() } returns emptyFlow()
    }
    private val entryRefresher by lazy {
        MangaEntryRefresher(
            updateMangaFromRemote = updateMangaFromRemote,
            filterChaptersForDownload = filterChaptersForDownload,
            downloadManager = downloadManager,
            dispatcher = vt.io,
        )
    }

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
        every { testAppGraph.mangaSourceManager } returns mockk<MangaSourceManager>(relaxed = true)
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } answers {
            Result.success(RemoteMangaUpdate(manga = secondArg(), newChapters = emptyList()))
        }
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `opening an initialized manga with chapters does not call the source`() = runTest(vt.scheduler) {
        createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        coVerify(exactly = 0) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `opening a manga that is not initialized fetches details only`() = runTest(vt.scheduler) {
        createModel(manga = manga(initialized = false), chapters = listOf(chapter()))
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
        coVerify { updateMangaFromRemote(any(), any(), fetchDetails = true, fetchChapters = false, any(), any()) }
    }

    @Test
    fun `opening a manga with no chapters fetches chapters only`() = runTest(vt.scheduler) {
        createModel(manga = manga(initialized = true), chapters = emptyList())
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
        coVerify { updateMangaFromRemote(any(), any(), fetchDetails = false, fetchChapters = true, any(), any()) }
    }

    @Test
    fun `a manual refresh fetches details and chapters`() = runTest(vt.scheduler) {
        val model = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        model.fetchAllFromSource()
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
        coVerify { updateMangaFromRemote(any(), any(), fetchDetails = true, fetchChapters = true, any(), any()) }
    }

    @Test
    fun `a manual refresh finishes and queues new chapters after the screen is closed`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        sourceReturnsAfter(gate, newChapters = listOf(chapter(id = 11L)))
        val model = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        model.fetchAllFromSource()
        advanceUntilIdle()
        model.viewModelScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        verify { downloadManager.downloadChapters(any(), listOf(chapter(id = 11L))) }
    }

    @Test
    fun `the refresh indicator shows while a manual refresh runs`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        sourceReturnsAfter(gate)
        val model = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        model.fetchAllFromSource()
        advanceUntilIdle()
        val whileRunning = model.successState().isRefreshingData
        gate.complete(Unit)
        advanceUntilIdle()

        whileRunning shouldBe true
        model.successState().isRefreshingData shouldBe false
    }

    @Test
    fun `a failed manual refresh shows the error in a snackbar`() = runTest(vt.scheduler) {
        val model = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("source down"))

        model.fetchAllFromSource()
        advanceUntilIdle()

        model.snackbarHostState.currentSnackbarData?.visuals?.message shouldBe "IllegalStateException: source down"
        model.successState().isRefreshingData shouldBe false
    }

    @Test
    fun `a second manual refresh while one runs does not fetch again`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        sourceReturnsAfter(gate)
        val model = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        model.fetchAllFromSource()
        model.fetchAllFromSource()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `reopening the screen while a refresh runs shows the indicator until it ends`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        sourceReturnsAfter(gate)
        val closed = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()
        closed.fetchAllFromSource()
        advanceUntilIdle()
        closed.viewModelScope.cancel()

        val reopened = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()
        val whileRunning = reopened.successState().isRefreshingData
        gate.complete(Unit)
        advanceUntilIdle()

        whileRunning shouldBe true
        reopened.successState().isRefreshingData shouldBe false
        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a manual refresh during the initial load does not fetch again`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        sourceReturnsAfter(gate)
        val model = createModel(manga = manga(initialized = false), chapters = listOf(chapter()))
        advanceUntilIdle()

        model.fetchAllFromSource()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
    }

    private fun sourceReturnsAfter(gate: CompletableDeferred<Unit>, newChapters: List<Chapter> = emptyList()) {
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } coAnswers {
            gate.await()
            Result.success(RemoteMangaUpdate(manga = secondArg(), newChapters = newChapters))
        }
    }

    private fun MangaScreenModel.successState() = state.value as MangaScreenModel.State.Success

    private fun createModel(manga: Manga, chapters: List<Chapter>): MangaScreenModel {
        val getMangaAndChapters = mockk<GetMangaWithChapters> {
            coEvery { subscribe(any(), any()) } returns emptyFlow()
            coEvery { awaitManga(manga.id) } returns manga
            coEvery { awaitChapters(manga.id, any()) } returns chapters
        }
        return MangaScreenModel(
            context = mockk(relaxed = true),
            lifecycle = mockk<Lifecycle>(relaxed = true),
            mangaId = manga.id,
            isFromSource = false,
            trackChapter = testAppGraph.trackChapter,
            downloadCache = testAppGraph.mangaDownloadCache,
            getDuplicateLibraryManga = mockk(relaxed = true),
            getAvailableScanlators = mockk(relaxed = true),
            getExcludedScanlators = mockk(relaxed = true),
            setExcludedScanlators = mockk(relaxed = true),
            setMangaChapterFlags = mockk(relaxed = true),
            setMangaDefaultChapterFlags = mockk(relaxed = true),
            setReadStatus = mockk(relaxed = true),
            updateChapter = testAppGraph.updateChapter,
            updateManga = testAppGraph.updateManga,
            getCategories = testAppGraph.getMangaCategories,
            addTracks = testAppGraph.addMangaTracks,
            setMangaCategories = mockk(relaxed = true),
            mangaRepository = testAppGraph.mangaRepository,
            mergeLibraryManga = mockk(relaxed = true),
            fetchInterval = testAppGraph.mangaFetchInterval,
            refreshTracksInteractor = mockk(relaxed = true),
            sourceManager = testAppGraph.mangaSourceManager,
            libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()),
            trackPreferences = TrackPreferences(InMemoryPreferenceStore()),
            readerPreferences = ReaderPreferences(InMemoryPreferenceStore()),
            trackerManager = mockk<TrackerManager> { every { loggedInTrackersFlow() } returns emptyFlow() },
            downloadManager = downloadManager,
            getMangaAndChapters = getMangaAndChapters,
            getTracks = mockk<GetMangaTracks> { every { subscribe(any<Long>()) } returns emptyFlow() },
            updateMangaFromRemote = updateMangaFromRemote,
            entryRefresher = entryRefresher,
            translationManager = mockk<TranslationManager> {
                every { translationStates } returns MutableStateFlow(emptyMap())
            },
            ioDispatcher = vt.io,
        )
    }

    private fun manga(initialized: Boolean) = Manga.create().copy(id = 1L, source = 1L, initialized = initialized)

    private fun chapter(id: Long = 10L) = Chapter.create().copy(id = id, mangaId = 1L)
}
