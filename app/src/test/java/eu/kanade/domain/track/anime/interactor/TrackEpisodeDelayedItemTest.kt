package eu.kanade.domain.track.anime.interactor

import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore
import eu.kanade.domain.track.anime.store.DelayedAnimeTrackingStore.DelayedAnimeTrackingItem
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.model.AnimeTrack

class TrackEpisodeDelayedItemTest {

    private val store = mockk<DelayedAnimeTrackingStore>(relaxed = true)

    @Test
    fun `a queued episode that the tracker already reached is removed`() = runTest {
        every { store.getAnimeItems() } returns listOf(DelayedAnimeTrackingItem(trackId = 7, lastEpisodeSeen = 10f))

        trackEpisode(trackedEpisode = 12.0).await(mockk(), animeId = 1, episodeNumber = 10.0)

        verify(exactly = 1) { store.removeAnimeItem(7) }
    }

    @Test
    fun `rewatching an old episode keeps a queued episode that the tracker has not reached`() = runTest {
        every { store.getAnimeItems() } returns listOf(DelayedAnimeTrackingItem(trackId = 7, lastEpisodeSeen = 15f))

        trackEpisode(trackedEpisode = 10.0).await(mockk(), animeId = 1, episodeNumber = 8.0)

        verify(exactly = 0) { store.removeAnimeItem(any()) }
    }

    private fun trackEpisode(trackedEpisode: Double): TrackEpisode {
        val track = AnimeTrack(
            id = 7,
            animeId = 1,
            trackerId = 2,
            remoteId = 3,
            libraryId = null,
            title = "Series",
            lastEpisodeSeen = trackedEpisode,
            totalEpisodes = 0,
            status = 0,
            score = 0.0,
            remoteUrl = "",
            startDate = 0,
            finishDate = 0,
            private = false,
        )
        return TrackEpisode(
            getTracks = mockk<GetAnimeTracks> { coEvery { await(1) } returns listOf(track) },
            trackerManager = mockk<TrackerManager> {
                every { get(2) } returns mockk { every { isLoggedIn } returns true }
            },
            insertTrack = mockk(relaxed = true),
            delayedTrackingStore = store,
        )
    }
}
