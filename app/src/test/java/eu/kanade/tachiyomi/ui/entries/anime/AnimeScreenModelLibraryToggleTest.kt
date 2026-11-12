package eu.kanade.tachiyomi.ui.entries.anime

import android.content.Context
import android.content.res.Resources
import androidx.lifecycle.Lifecycle
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.entries.anime.interactor.GetDuplicateLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.i18n.MR

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeScreenModelLibraryToggleTest {

    private val vt = VirtualTime()
    private val resources = mockk<Resources> {
        every { getString(any()) } answers { "string-${firstArg<Int>()}" }
    }
    private val context = mockk<Context>(relaxed = true) {
        every { resources } returns this@AnimeScreenModelLibraryToggleTest.resources
    }
    private val updateAnime = mockk<UpdateAnime>(relaxed = true)
    private val addTracks = mockk<AddAnimeTracks>(relaxed = true)
    private val downloadManager = mockk<AnimeDownloadManager>(relaxed = true) {
        every { queueState } returns MutableStateFlow(emptyList())
        every { statusFlow() } returns emptyFlow()
        every { progressFlow() } returns emptyFlow()
        every { getDownloadCount(any<Anime>()) } returns 0
    }

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
        every { testAppGraph.animeSourceManager } returns mockk<AnimeSourceManager>(relaxed = true)
        every { testAppGraph.basePreferences } returns BasePreferences(mockk(relaxed = true), InMemoryPreferenceStore())
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `a second tap while the first write runs does not write again`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } coAnswers {
            gate.await()
            true
        }
        val model = loadedModel(anime(favorite = false))

        model.toggleFavorite()
        advanceUntilIdle()
        model.toggleFavorite()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateAnime.awaitUpdateFavorite(1L, true) }
    }

    @Test
    fun `a tap after the add finishes removes the title`() = runTest(vt.scheduler) {
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns true
        val model = loadedModel(anime(favorite = false))

        model.toggleFavorite()
        advanceUntilIdle()
        model.toggleFavorite()
        advanceUntilIdle()

        coVerify(exactly = 1) { updateAnime.awaitUpdateFavorite(1L, true) }
        coVerify(exactly = 1) { updateAnime.awaitUpdateFavorite(1L, false) }
    }

    @Test
    fun `retry after a later successful add does not write again`() = runTest(vt.scheduler) {
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns false
        val model = loadedModel(anime(favorite = false))
        model.toggleFavorite()
        advanceUntilIdle()
        val snackbar = model.snackbarHostState.currentSnackbarData
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns true
        model.toggleFavorite()
        advanceUntilIdle()

        snackbar shouldNotBe null
        snackbar?.performAction()
        advanceUntilIdle()

        coVerify(exactly = 2) { updateAnime.awaitUpdateFavorite(1L, true) }
        coVerify(exactly = 0) { updateAnime.awaitUpdateFavorite(1L, false) }
    }

    @Test
    fun `a tap while a category picker write runs does not write again`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } coAnswers {
            gate.await()
            true
        }
        val title = anime(favorite = false)
        val model = loadedModel(title)

        model.moveAnimeToCategoriesAndAddToLibrary(title, listOf(5L))
        advanceUntilIdle()
        model.toggleFavorite()
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateAnime.awaitUpdateFavorite(1L, true) }
    }

    @Test
    fun `only a successful change confirms the library button`() = runTest(vt.scheduler) {
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns false
        val model = loadedModel(anime(favorite = false))
        model.toggleFavorite()
        advanceUntilIdle()
        val afterFailure = model.successState().libraryConfirmations

        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns true
        model.toggleFavorite()
        advanceUntilIdle()

        afterFailure shouldBe 0
        model.successState().libraryConfirmations shouldBe 1
    }

    @Test
    fun `a picker confirm while trackers bind adds the title`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        coEvery { addTracks.bindEnhancedTrackers(any(), any()) } coAnswers { gate.await() }
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns true
        val title = anime(favorite = false)
        val model = loadedModel(title, categories = listOf(category()))

        model.toggleFavorite()
        advanceUntilIdle()
        model.moveAnimeToCategoriesAndAddToLibrary(title, listOf(5L))
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateAnime.awaitUpdateFavorite(1L, true) }
    }

    @Test
    fun `a failed add offers a retry`() = runTest(vt.scheduler) {
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns false
        val model = loadedModel(anime(favorite = false))

        model.toggleFavorite()
        advanceUntilIdle()

        model.failureSnackbarAction(MR.strings.library_add_failed) shouldBe
            context.stringResource(MR.strings.action_retry)
        coVerify(exactly = 1) { updateAnime.awaitUpdateFavorite(1L, true) }
    }

    @Test
    fun `a failed remove offers a retry`() = runTest(vt.scheduler) {
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns false
        val model = loadedModel(anime(favorite = true))

        model.toggleFavorite()
        advanceUntilIdle()

        model.failureSnackbarAction(MR.strings.library_remove_failed) shouldBe
            context.stringResource(MR.strings.action_retry)
        coVerify(exactly = 1) { updateAnime.awaitUpdateFavorite(1L, false) }
    }

    @Test
    fun `retry on a failed add writes again`() = runTest(vt.scheduler) {
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns false
        val model = loadedModel(anime(favorite = false))
        model.toggleFavorite()
        advanceUntilIdle()

        val snackbar = model.snackbarHostState.currentSnackbarData
        snackbar shouldNotBe null
        snackbar?.performAction()
        advanceUntilIdle()

        coVerify(exactly = 2) { updateAnime.awaitUpdateFavorite(1L, true) }
    }

    @Test
    fun `a failed add from the category picker offers a retry`() = runTest(vt.scheduler) {
        coEvery { updateAnime.awaitUpdateFavorite(any(), any()) } returns false
        val title = anime(favorite = false)
        val model = loadedModel(title)

        model.moveAnimeToCategoriesAndAddToLibrary(title, listOf(5L))
        advanceUntilIdle()

        model.failureSnackbarAction(MR.strings.library_add_failed) shouldBe
            context.stringResource(MR.strings.action_retry)
        coVerify(exactly = 1) { updateAnime.awaitUpdateFavorite(1L, true) }
    }

    private fun AnimeScreenModel.successState() = state.value as AnimeScreenModel.State.Success

    private fun AnimeScreenModel.failureSnackbarAction(message: StringResource): String? {
        val visuals = snackbarHostState.currentSnackbarData?.visuals
        visuals?.message shouldBe context.stringResource(message)
        return visuals?.actionLabel
    }

    private suspend fun TestScope.loadedModel(
        anime: Anime,
        categories: List<Category> = emptyList(),
    ): AnimeScreenModel {
        val model = createModel(anime, categories)
        model.state.first { it is AnimeScreenModel.State.Success }
        advanceUntilIdle()
        return model
    }

    private fun createModel(anime: Anime, categories: List<Category> = emptyList()): AnimeScreenModel {
        val getAnimeAndEpisodesAndSeasons = mockk<GetAnimeWithEpisodesAndSeasons> {
            coEvery { subscribe(any()) } returns emptyFlow()
            coEvery { awaitAnime(anime.id) } returns anime
            coEvery { awaitEpisodes(anime.id) } returns listOf(Episode.create().copy(id = 10L, animeId = anime.id))
            coEvery { awaitSeasons(anime.id) } returns emptyList()
        }
        return AnimeScreenModel(
            context = context,
            lifecycle = mockk<Lifecycle>(relaxed = true),
            animeId = anime.id,
            isFromSource = false,
            downloadPreferences = DownloadPreferences(InMemoryPreferenceStore()),
            libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()),
            trackPreferences = TrackPreferences(InMemoryPreferenceStore()),
            playerPreferences = PlayerPreferences(InMemoryPreferenceStore()),
            gesturePreferences = GesturePreferences(InMemoryPreferenceStore()),
            trackerManager = mockk<TrackerManager> { every { loggedInTrackersFlow() } returns emptyFlow() },
            trackEpisode = mockk(relaxed = true),
            sourceManager = testAppGraph.animeSourceManager,
            downloadManager = downloadManager,
            downloadCache = mockk(relaxed = true),
            getAnimeAndEpisodesAndSeasons = getAnimeAndEpisodesAndSeasons,
            getDuplicateLibraryAnime = mockk<GetDuplicateLibraryAnime> {
                coEvery { awaitAll(any()) } returns emptyList()
            },
            setAnimeEpisodeFlags = mockk(relaxed = true),
            setAnimeDefaultEpisodeFlags = mockk(relaxed = true),
            setAnimeSeasonFlags = mockk(relaxed = true),
            setAnimeDefaultSeasonFlags = mockk(relaxed = true),
            setSeenStatus = mockk(relaxed = true),
            updateEpisode = mockk(relaxed = true),
            updateAnime = updateAnime,
            getCategories = mockk<GetAnimeCategories>(relaxed = true) {
                coEvery { await() } returns categories
                coEvery { await(any<Long>()) } returns emptyList()
                every { subscribe(any()) } returns emptyFlow()
            },
            getTracks = mockk<GetAnimeTracks> { every { subscribe(any<Long>()) } returns emptyFlow() },
            addTracks = addTracks,
            setAnimeCategories = mockk(relaxed = true),
            animeRepository = mockk(relaxed = true),
            getEpisodesByAnimeId = mockk(relaxed = true),
            setAnimeViewerFlags = mockk(relaxed = true),
            mergeLibraryAnime = mockk(relaxed = true),
            fetchInterval = mockk(relaxed = true),
            getRelatedAnime = mockk { every { subscribe(any()) } returns kotlinx.coroutines.flow.emptyFlow() },
            syncRelatedAnimeWithSource = mockk(relaxed = true),
            entryRefresher = mockk<AnimeEntryRefresher>(relaxed = true) {
                every { running(any()) } returns null
                coEvery { fetchFromSource(any(), any(), any(), any(), any()) } returns (null to null)
            },
            ioDispatcher = vt.io,
            refreshTracksInteractor = mockk(relaxed = true),
        )
    }

    private fun category() = Category(id = 5L, name = "Reading", order = 1L, flags = 0L, hidden = false)

    private fun anime(favorite: Boolean) =
        Anime.create().copy(id = 1L, source = 1L, initialized = true, favorite = favorite)
}
