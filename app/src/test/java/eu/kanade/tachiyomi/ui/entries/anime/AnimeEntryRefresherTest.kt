package eu.kanade.tachiyomi.ui.entries.anime

import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.SyncSeasonsWithSource
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.PopulateFillerMarks
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.util.AniChartApi
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeEntryRefresherTest {

    private val anime = Anime.create().copy(id = 1L, source = 1L, favorite = true, fetchType = FetchType.Episodes)
    private val newEpisode = Episode.create().copy(id = 10L, animeId = 1L)
    private val airing = Pair(4, 1_700_000_000L)
    private val source = mockk<AnimeSource> {
        every { name } returns "Test"
        coEvery { getAnimeDetails(any()) } returns SAnime.create()
        coEvery { getEpisodeList(any()) } returns listOf(SEpisode.create())
    }
    private val updateAnime = mockk<UpdateAnime>(relaxed = true)
    private val syncEpisodesWithSource = mockk<SyncEpisodesWithSource> {
        coEvery { await(any(), any(), any(), any(), any()) } returns listOf(newEpisode)
    }
    private val syncSeasonsWithSource = mockk<SyncSeasonsWithSource>()
    private val populateFillerMarks = mockk<PopulateFillerMarks>(relaxed = true)
    private val getEpisodesByAnimeId = mockk<GetEpisodesByAnimeId> {
        coEvery { await(any()) } returns emptyList()
    }
    private val filterEpisodesForDownload = mockk<FilterEpisodesForDownload> {
        coEvery { await(any(), any()) } answers { secondArg() }
    }
    private val downloadManager = mockk<AnimeDownloadManager>(relaxed = true)
    private val libraryPreferences = mockk<LibraryPreferences> {
        every { updateSeasonOnRefresh().get() } returns true
    }
    private val animeRepository = mockk<AnimeRepository> {
        coEvery { getAnimeById(1L) } returns anime
    }
    private val setAnimeViewerFlags = mockk<SetAnimeViewerFlags>(relaxed = true)
    private val aniChartApi = mockk<AniChartApi> {
        coEvery { loadAiringTime(any(), any(), any()) } returns airing
    }

    @BeforeEach
    fun setUp() {
        testAppGraph
    }

    @Test
    fun `a refresh saves the details and episodes, queues new episodes, and updates the airing time`() = runTest {
        val result = refresher().refresh(source, anime, emptyList()).await()

        result shouldBe AnimeRefreshResult(detailsError = null, itemsError = null, nextAiringEpisode = airing)
        coVerify { updateAnime.awaitUpdateFromSource(anime, any(), true) }
        coVerify { syncEpisodesWithSource.await(any(), anime, source, true, any()) }
        verify { downloadManager.downloadEpisodes(anime, listOf(newEpisode)) }
        coVerify { setAnimeViewerFlags.awaitSetNextEpisodeAiring(1L, airing) }
    }

    @Test
    fun `a refresh runs to the end after its caller is cancelled`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { source.getEpisodeList(any()) } coAnswers {
            gate.await()
            listOf(SEpisode.create())
        }
        val refresher = refresher()

        val caller = launch { refresher.refresh(source, anime, emptyList()).await() }
        advanceUntilIdle()
        caller.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        verify { downloadManager.downloadEpisodes(anime, listOf(newEpisode)) }
        coVerify { setAnimeViewerFlags.awaitSetNextEpisodeAiring(1L, airing) }
    }

    @Test
    fun `a details failure is returned and the episodes are still saved`() = runTest {
        val error = IllegalStateException("details down")
        coEvery { source.getAnimeDetails(any()) } throws error

        val result = refresher().refresh(source, anime, emptyList()).await()

        result.detailsError shouldBe error
        result.itemsError shouldBe null
        coVerify { syncEpisodesWithSource.await(any(), anime, source, true, any()) }
    }

    @Test
    fun `a refresh of a season anime saves the seasons and the episodes of unfinished seasons`() = runTest {
        val seasonAnime = anime.copy(fetchType = FetchType.Seasons)
        val season = Anime.create().copy(id = 2L, source = 1L, fetchType = FetchType.Episodes)
        coEvery { source.getSeasonList(any()) } returns listOf(SAnime.create())
        coEvery { syncSeasonsWithSource.await(any(), seasonAnime, source, any(), any()) } returns listOf(season)

        refresher().refresh(source, seasonAnime, emptyList()).await()

        coVerify { syncEpisodesWithSource.await(any(), season, source, true, any()) }
        verify { downloadManager.downloadEpisodes(seasonAnime, listOf(newEpisode)) }
    }

    @Test
    fun `a second refresh of the same entry joins the running one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { source.getEpisodeList(any()) } coAnswers {
            gate.await()
            listOf(SEpisode.create())
        }
        val refresher = refresher()

        val first = refresher.refresh(source, anime, emptyList())
        val second = refresher.refresh(source, anime, emptyList())
        gate.complete(Unit)
        advanceUntilIdle()

        (first === second) shouldBe true
        coVerify(exactly = 1) { syncEpisodesWithSource.await(any(), any(), any(), any(), any()) }
    }

    private fun TestScope.refresher() = AnimeEntryRefresher(
        updateAnime = updateAnime,
        syncEpisodesWithSource = syncEpisodesWithSource,
        syncSeasonsWithSource = syncSeasonsWithSource,
        populateFillerMarks = populateFillerMarks,
        getEpisodesByAnimeId = getEpisodesByAnimeId,
        filterEpisodesForDownload = filterEpisodesForDownload,
        downloadManager = downloadManager,
        libraryPreferences = libraryPreferences,
        animeRepository = animeRepository,
        setAnimeViewerFlags = setAnimeViewerFlags,
        aniChartApi = aniChartApi,
        dispatcher = StandardTestDispatcher(testScheduler),
    )
}
