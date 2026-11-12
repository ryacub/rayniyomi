package eu.kanade.tachiyomi.ui.entries.anime

import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.SyncSeasonsWithSource
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.items.episode.interactor.PopulateFillerMarks
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryAnimeDb
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.data.items.episode.EpisodeRepositoryImpl
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.ShouldUpdateDbEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeEntryRefresherTest {

    private val anime = Anime.create().copy(id = 1L, source = 1L, favorite = true, fetchType = FetchType.Episodes)
    private val newEpisode = Episode.create().copy(id = 10L, animeId = 1L)
    private val airing = Pair(4, 1_700_000_000L)
    private val source = LegacySource()

    private class LegacySource : AnimeSource {
        override val id = 1L
        override val name = "Legacy"
        var details: suspend () -> SAnime = { SAnime.create() }
        var episodes: suspend () -> List<SEpisode> = { listOf(SEpisode.create()) }
        var seasons: suspend () -> List<SAnime> = { emptyList() }

        override suspend fun getAnimeDetails(anime: SAnime) = details()
        override suspend fun getEpisodeList(anime: SAnime) = episodes()
        override suspend fun getSeasonList(anime: SAnime) = seasons()
    }
    private val updateAnime = mockk<UpdateAnime>(relaxed = true)
    private val syncEpisodesWithSource = mockk<SyncEpisodesWithSource> {
        coEvery { await(any(), any(), any(), any(), any(), matchNullable { true }) } returns listOf(newEpisode)
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
        source.episodes = {
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
        source.details = { throw error }

        val result = refresher().refresh(source, anime, emptyList()).await()

        result.detailsError shouldBe error
        result.itemsError shouldBe null
        coVerify { syncEpisodesWithSource.await(any(), anime, source, true, any()) }
    }

    @Test
    fun `a refresh of a season anime saves the seasons and the episodes of unfinished seasons`() = runTest {
        val seasonAnime = anime.copy(fetchType = FetchType.Seasons)
        val season = Anime.create().copy(id = 2L, source = 1L, fetchType = FetchType.Episodes)
        source.seasons = { listOf(SAnime.create()) }
        coEvery { syncSeasonsWithSource.await(any(), seasonAnime, source, any(), any()) } returns listOf(season)

        refresher().refresh(source, seasonAnime, emptyList()).await()

        coVerify { syncEpisodesWithSource.await(any(), season, source, true, any()) }
        verify { downloadManager.downloadEpisodes(seasonAnime, listOf(newEpisode)) }
    }

    @Test
    fun `a second refresh of the same entry joins the running one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.episodes = {
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

    @Test
    fun `the running refresh of an entry is found until it ends`() = runTest {
        val gate = CompletableDeferred<Unit>()
        source.episodes = {
            gate.await()
            listOf(SEpisode.create())
        }
        val refresher = refresher()

        val refresh = refresher.refresh(source, anime, emptyList())
        advanceUntilIdle()
        val whileRunning = refresher.running(anime.id)
        gate.complete(Unit)
        advanceUntilIdle()

        (whileRunning === refresh) shouldBe true
        refresher.running(anime.id) shouldBe null
    }

    @Test
    fun `a lib 17 refresh sends existing episodes and requests both updates once`() = runTest {
        val first = newEpisode.copy(url = "new", name = "New", sourceOrder = 0)
        val second = newEpisode.copy(url = "old", name = "Old", sourceOrder = 1)
        coEvery { getEpisodesByAnimeId.await(anime.id) } returns listOf(second, first)
        val returnedAnime = SAnime.create()
        val returnedEpisodes = listOf(SEpisode.create())
        var combinedCalls = 0
        val lib17 = object : AnimeSource {
            override val id = 17L
            override val name = "Lib 17"

            override suspend fun getAnimeEpisodeUpdate(
                anime: SAnime,
                episodes: List<SEpisode>,
                fetchDetails: Boolean,
                fetchEpisodes: Boolean,
            ): SAnimeEpisodeUpdate {
                episodes.map { it.url } shouldBe listOf(first.url, second.url)
                fetchDetails shouldBe true
                fetchEpisodes shouldBe true
                combinedCalls++
                return SAnimeEpisodeUpdate(returnedAnime, returnedEpisodes)
            }
        }

        val result = refresher().refresh(lib17, anime, emptyList()).await()

        result shouldBe AnimeRefreshResult(detailsError = null, itemsError = null, nextAiringEpisode = airing)
        combinedCalls shouldBe 1
        coVerify { updateAnime.awaitUpdateFromSource(anime, returnedAnime, true) }
        coVerify { syncEpisodesWithSource.await(returnedEpisodes, anime, lib17, true, any()) }
    }

    @Test
    fun `an items-only refresh saves anime memo without replacing metadata`() = runTest {
        val memo = JsonObject(mapOf("cursor" to JsonPrimitive(2)))
        val lib17 = object : AnimeSource {
            override val id = 17L
            override val name = "Lib 17"

            override suspend fun getAnimeEpisodeUpdate(
                anime: SAnime,
                episodes: List<SEpisode>,
                fetchDetails: Boolean,
                fetchEpisodes: Boolean,
            ): SAnimeEpisodeUpdate {
                fetchDetails shouldBe false
                fetchEpisodes shouldBe true
                return SAnimeEpisodeUpdate(anime.apply { this.memo = memo }, listOf(SEpisode.create()))
            }
        }

        refresher().fetchFromSource(lib17, anime, fetchDetails = false, fetchItems = true) shouldBe (null to null)

        coVerify { updateAnime.await(AnimeUpdate(id = anime.id, memo = memo)) }
        coVerify(exactly = 0) { updateAnime.awaitUpdateFromSource(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a details-only refresh saves items returned by the source`() = runTest {
        val episode = SEpisode.create().apply {
            url = "updated"
            name = "Updated"
            memo = JsonObject(mapOf("cursor" to JsonPrimitive(3)))
        }
        val lib17 = object : AnimeSource {
            override val id = 17L
            override val name = "Lib 17"

            override suspend fun getAnimeEpisodeUpdate(
                anime: SAnime,
                episodes: List<SEpisode>,
                fetchDetails: Boolean,
                fetchEpisodes: Boolean,
            ): SAnimeEpisodeUpdate {
                fetchDetails shouldBe true
                fetchEpisodes shouldBe false
                return SAnimeEpisodeUpdate(anime, listOf(episode))
            }
        }

        refresher().fetchFromSource(lib17, anime, fetchDetails = true, fetchItems = false) shouldBe (null to null)

        coVerify {
            syncEpisodesWithSource.await(listOf(episode), anime, lib17, false, any(), expectedUrls = emptyList())
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["new episode", "updated memo"])
    fun `a details-only refresh keeps changes made during the source call`(change: String) = runTest {
        InMemoryAnimeDb().use { db ->
            db.insertAnime(id = anime.id, url = "/series", title = "Series")
            db.insertEpisode(id = 10, animeId = anime.id, url = "/old")
            val repository = EpisodeRepositoryImpl(db.handler)
            val getEpisodes = GetEpisodesByAnimeId(repository)
            val sync = SyncEpisodesWithSource(
                downloadManager = downloadManager,
                downloadProvider = mockk(relaxed = true),
                episodeRepository = repository,
                shouldUpdateDbEpisode = ShouldUpdateDbEpisode(),
                updateAnime = updateAnime,
                getEpisodesByAnimeId = getEpisodes,
                libraryPreferences = mockk<LibraryPreferences> {
                    every { markDuplicateSeenEpisodeAsSeen().get() } returns emptySet()
                },
            )
            val lib17 = object : AnimeSource {
                override val id = 17L
                override val name = "Lib 17"

                override suspend fun getAnimeEpisodeUpdate(
                    anime: SAnime,
                    episodes: List<SEpisode>,
                    fetchDetails: Boolean,
                    fetchEpisodes: Boolean,
                ): SAnimeEpisodeUpdate {
                    episodes.map { it.url } shouldBe listOf("/old")
                    if (change == "new episode") {
                        db.insertEpisode(id = 11, animeId = 1, url = "/concurrent")
                        db.driver.execute(null, "UPDATE episodes SET seen = 1 WHERE _id = 11", 0)
                    } else {
                        db.driver.execute(null, """UPDATE episodes SET memo = '{"cursor":7}' WHERE _id = 10""", 0)
                    }
                    return SAnimeEpisodeUpdate(anime, episodes)
                }
            }

            refresher(sync, getEpisodes).fetchFromSource(lib17, anime, fetchDetails = true, fetchItems = false)
                .shouldBe(null to null)

            val stored = getEpisodes.await(anime.id).associateBy { it.url }
            if (change == "new episode") {
                stored.keys shouldBe setOf("/old", "/concurrent")
                stored.getValue("/concurrent").seen shouldBe true
            } else {
                stored.getValue("/old").memo shouldBe JsonObject(mapOf("cursor" to JsonPrimitive(7)))
            }
        }
    }

    private fun TestScope.refresher(
        episodeSync: SyncEpisodesWithSource = syncEpisodesWithSource,
        getEpisodes: GetEpisodesByAnimeId = getEpisodesByAnimeId,
    ) = AnimeEntryRefresher(
        updateAnime = updateAnime,
        syncEpisodesWithSource = episodeSync,
        syncSeasonsWithSource = syncSeasonsWithSource,
        populateFillerMarks = populateFillerMarks,
        getEpisodesByAnimeId = getEpisodes,
        filterEpisodesForDownload = filterEpisodesForDownload,
        downloadManager = downloadManager,
        libraryPreferences = libraryPreferences,
        animeRepository = animeRepository,
        setAnimeViewerFlags = setAnimeViewerFlags,
        aniChartApi = aniChartApi,
        dispatcher = StandardTestDispatcher(testScheduler),
    )
}
