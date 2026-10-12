package eu.kanade.tachiyomi.ui.stats.anime

import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.test.VirtualTime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.model.AnimeTrack

class AnimeStatsScreenModelTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() = vt.setUpMain()

    @AfterEach
    fun tearDown() = vt.tearDownMain()

    @Test
    fun `tracked title count comes from one track query`() = runBlocking {
        val libraryAnime = listOf(libraryAnime(1L), libraryAnime(2L), libraryAnime(3L))
        val tracks = listOf(
            track(animeId = 1L, trackerId = LOGGED_IN_TRACKER),
            track(animeId = 2L, trackerId = LOGGED_OUT_TRACKER),
            track(animeId = 99L, trackerId = LOGGED_IN_TRACKER),
        )
        val getTracks = mockk<GetAnimeTracks>()
        coEvery { getTracks.awaitAll() } returns tracks
        coEvery { getTracks.await(any()) } answers { tracks.filter { it.animeId == firstArg<Long>() } }

        val model = createModel(libraryAnime, getTracks)
        val state = withContext(Dispatchers.Default) {
            withTimeout(5_000) { model.state.first { it is StatsScreenState.SuccessAnime } }
        } as StatsScreenState.SuccessAnime

        assertEquals(1, state.trackers.trackedTitleCount)
        coVerify(exactly = 0) { getTracks.await(any()) }
    }

    private fun createModel(libraryAnime: List<LibraryAnime>, getTracks: GetAnimeTracks): AnimeStatsScreenModel {
        val getLibraryAnime = mockk<GetLibraryAnime>()
        coEvery { getLibraryAnime.await() } returns libraryAnime
        val getEpisodesByAnimeId = mockk<GetEpisodesByAnimeId>()
        coEvery { getEpisodesByAnimeId.await(any()) } returns emptyList()
        val downloadManager = mockk<AnimeDownloadManager>()
        every { downloadManager.getDownloadCount() } returns 0
        val tracker = mockk<BaseTracker>(moreInterfaces = arrayOf(AnimeTracker::class))
        every { tracker.id } returns LOGGED_IN_TRACKER
        val trackerManager = mockk<TrackerManager>()
        every { trackerManager.loggedInTrackers() } returns listOf(tracker)

        return AnimeStatsScreenModel(
            downloadManager = downloadManager,
            getAnimelibAnime = getLibraryAnime,
            getEpisodesByAnimeId = getEpisodesByAnimeId,
            getTracks = getTracks,
            preferences = LibraryPreferences(InMemoryPreferenceStore(emptySequence())),
            trackerManager = trackerManager,
        )
    }

    private fun libraryAnime(id: Long) = LibraryAnime(
        anime = Anime.create().copy(id = id),
        category = 0L,
        totalCount = 0L,
        seenCount = 0L,
        bookmarkCount = 0L,
        fillermarkCount = 0L,
        latestUpload = 0L,
        episodeFetchedAt = 0L,
        lastSeen = 0L,
    )

    private fun track(animeId: Long, trackerId: Long) = AnimeTrack(
        id = animeId * 100 + trackerId,
        animeId = animeId,
        trackerId = trackerId,
        remoteId = 0L,
        libraryId = null,
        title = "",
        lastEpisodeSeen = 0.0,
        totalEpisodes = 0L,
        status = 0L,
        score = 0.0,
        remoteUrl = "",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )

    private companion object {
        const val LOGGED_IN_TRACKER = 1L
        const val LOGGED_OUT_TRACKER = 2L
    }
}
