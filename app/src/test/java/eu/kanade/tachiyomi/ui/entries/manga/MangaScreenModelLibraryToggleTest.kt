package eu.kanade.tachiyomi.ui.entries.manga

import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.Lifecycle
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.entries.manga.interactor.GetDuplicateLibraryManga
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.i18n.MR

@OptIn(ExperimentalCoroutinesApi::class)
class MangaScreenModelLibraryToggleTest {

    private val vt = VirtualTime()
    private val resources = mockk<Resources> {
        every { getString(any()) } answers { "string-${firstArg<Int>()}" }
    }
    private val context = mockk<Context>(relaxed = true) {
        every { resources } returns this@MangaScreenModelLibraryToggleTest.resources
    }
    private val updateManga = mockk<UpdateManga>(relaxed = true)
    private val downloadManager = mockk<MangaDownloadManager>(relaxed = true) {
        every { queueState } returns MutableStateFlow(emptyList())
        every { statusFlow() } returns emptyFlow()
        every { progressFlow() } returns emptyFlow()
        every { getDownloadCount(any<Manga>()) } returns 0
    }

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
        every { testAppGraph.mangaSourceManager } returns mockk<MangaSourceManager>(relaxed = true)
        every { testAppGraph.basePreferences } returns BasePreferences(mockk(relaxed = true), InMemoryPreferenceStore())
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `a second tap while the first write runs does not write again`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } coAnswers {
            gate.await()
            true
        }
        val model = createModel(manga(favorite = false))
        advanceUntilIdle()

        model.toggleFavorite()
        advanceUntilIdle()
        model.toggleFavorite()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateManga.awaitUpdateFavorite(1L, true) }
    }

    @Test
    fun `a tap after the add finishes removes the title`() = runTest(vt.scheduler) {
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } returns true
        val model = createModel(manga(favorite = false))
        advanceUntilIdle()

        model.toggleFavorite()
        advanceUntilIdle()
        model.toggleFavorite()
        advanceUntilIdle()

        coVerify(exactly = 1) { updateManga.awaitUpdateFavorite(1L, true) }
        coVerify(exactly = 1) { updateManga.awaitUpdateFavorite(1L, false) }
    }

    @Test
    fun `retry after a later successful add does not write again`() = runTest(vt.scheduler) {
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } returns false
        val model = createModel(manga(favorite = false))
        advanceUntilIdle()
        model.toggleFavorite()
        advanceUntilIdle()
        val snackbar = model.snackbarHostState.currentSnackbarData
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } returns true
        model.toggleFavorite()
        advanceUntilIdle()

        snackbar shouldNotBe null
        snackbar?.performAction()
        advanceUntilIdle()

        coVerify(exactly = 2) { updateManga.awaitUpdateFavorite(1L, true) }
    }

    @Test
    fun `a tap while a category picker write runs does not write again`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } coAnswers {
            gate.await()
            true
        }
        val title = manga(favorite = false)
        val model = createModel(title)
        advanceUntilIdle()

        model.moveMangaToCategoriesAndAddToLibrary(title, listOf(5L))
        advanceUntilIdle()
        model.toggleFavorite()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateManga.awaitUpdateFavorite(1L, true) }
    }

    @Test
    fun `only a successful change confirms the library button`() = runTest(vt.scheduler) {
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } returns false
        val model = createModel(manga(favorite = false))
        advanceUntilIdle()
        model.toggleFavorite()
        advanceUntilIdle()
        val afterFailure = model.successState().libraryConfirmations

        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } returns true
        model.toggleFavorite()
        advanceUntilIdle()

        afterFailure shouldBe 0
        model.successState().libraryConfirmations shouldBe 1
    }

    @Test
    fun `a failed add offers a retry`() = runTest(vt.scheduler) {
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } returns false
        val model = createModel(manga(favorite = false))
        advanceUntilIdle()

        model.toggleFavorite()
        advanceUntilIdle()

        model.failureSnackbarAction(MR.strings.library_add_failed) shouldBe
            context.stringResource(MR.strings.action_retry)
        coVerify(exactly = 1) { updateManga.awaitUpdateFavorite(1L, true) }
    }

    @Test
    fun `a failed remove offers a retry`() = runTest(vt.scheduler) {
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } returns false
        val model = createModel(manga(favorite = true))
        advanceUntilIdle()

        model.toggleFavorite()
        advanceUntilIdle()

        model.failureSnackbarAction(MR.strings.library_remove_failed) shouldBe
            context.stringResource(MR.strings.action_retry)
        coVerify(exactly = 1) { updateManga.awaitUpdateFavorite(1L, false) }
    }

    @Test
    fun `retry on a failed add writes again`() = runTest(vt.scheduler) {
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } returns false
        val model = createModel(manga(favorite = false))
        advanceUntilIdle()
        model.toggleFavorite()
        advanceUntilIdle()

        val snackbar = model.snackbarHostState.currentSnackbarData
        snackbar shouldNotBe null
        snackbar?.performAction()
        advanceUntilIdle()

        coVerify(exactly = 2) { updateManga.awaitUpdateFavorite(1L, true) }
    }

    @Test
    fun `a failed add from the category picker offers a retry`() = runTest(vt.scheduler) {
        coEvery { updateManga.awaitUpdateFavorite(any(), any()) } returns false
        val title = manga(favorite = false)
        val model = createModel(title)
        advanceUntilIdle()

        model.moveMangaToCategoriesAndAddToLibrary(title, listOf(5L))
        advanceUntilIdle()

        model.failureSnackbarAction(MR.strings.library_add_failed) shouldBe
            context.stringResource(MR.strings.action_retry)
        coVerify(exactly = 1) { updateManga.awaitUpdateFavorite(1L, true) }
    }

    private fun MangaScreenModel.successState() = state.value as MangaScreenModel.State.Success

    private fun MangaScreenModel.failureSnackbarAction(message: StringResource): String? {
        val visuals = snackbarHostState.currentSnackbarData?.visuals
        visuals?.message shouldBe context.stringResource(message)
        return visuals?.actionLabel
    }

    private fun createModel(manga: Manga): MangaScreenModel {
        val getMangaAndChapters = mockk<GetMangaWithChapters> {
            coEvery { subscribe(any(), any()) } returns emptyFlow()
            coEvery { awaitManga(manga.id) } returns manga
            coEvery { awaitChapters(manga.id, any()) } returns emptyList()
        }
        return MangaScreenModel(
            context = context,
            lifecycle = mockk<Lifecycle>(relaxed = true),
            mangaId = manga.id,
            isFromSource = false,
            trackChapter = testAppGraph.trackChapter,
            downloadCache = testAppGraph.mangaDownloadCache,
            getDuplicateLibraryManga = mockk<GetDuplicateLibraryManga> {
                coEvery { awaitAll(any()) } returns emptyList()
            },
            getAvailableScanlators = mockk(relaxed = true),
            getExcludedScanlators = mockk(relaxed = true),
            setExcludedScanlators = mockk(relaxed = true),
            setMangaChapterFlags = mockk(relaxed = true),
            setMangaDefaultChapterFlags = mockk(relaxed = true),
            setReadStatus = mockk(relaxed = true),
            updateChapter = testAppGraph.updateChapter,
            updateManga = updateManga,
            getCategories = mockk<GetMangaCategories>(relaxed = true) {
                coEvery { await() } returns emptyList()
            },
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
            updateMangaFromRemote = mockk(relaxed = true),
            entryRefresher = mockk<MangaEntryRefresher>(relaxed = true) { every { running(any()) } returns null },
            translationManager = mockk<TranslationManager> {
                every { translationStates } returns MutableStateFlow(emptyMap())
            },
            ioDispatcher = vt.io,
        )
    }

    private fun manga(favorite: Boolean) =
        Manga.create().copy(id = 1L, source = 1L, initialized = true, favorite = favorite)
}
