package eu.kanade.domain.track

import eu.kanade.domain.track.anime.interactor.RefreshAllAnimeTracks
import eu.kanade.domain.track.interactor.TrackSyncConflictResolver
import eu.kanade.domain.track.manga.interactor.RefreshAllMangaTracks
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.MalformedTrackerResponseException
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.track.anime.interactor.DeleteAnimeTrack
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.manga.interactor.DeleteMangaTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

class MalformedTrackerResponseRoutingTest {

    @Test
    fun `malformed anime response becomes a failure instead of unlinking`() = runTest {
        val tracker = mockk<BaseTracker>(relaxed = true, moreInterfaces = arrayOf(AnimeTracker::class))
        val animeTracker = tracker as AnimeTracker
        val trackerManager = mockk<TrackerManager>()
        val getTracks = mockk<GetAnimeTracks>()
        val deleteTrack = mockk<DeleteAnimeTrack>(relaxed = true)
        val localTrack = animeTrack()
        val malformedResponse = MalformedTrackerResponseException("Simkl", "total episode count")

        every { tracker.id } returns localTrack.trackerId
        every { tracker.isLoggedIn } returns true
        every { trackerManager.get(localTrack.trackerId) } returns tracker
        coEvery { getTracks.awaitAll() } returns listOf(localTrack)
        coEvery { animeTracker.refresh(any()) } throws malformedResponse

        val result = RefreshAllAnimeTracks(
            getTracks = getTracks,
            trackerManager = trackerManager,
            insertTrack = mockk<InsertAnimeTrack>(relaxed = true),
            deleteTrack = deleteTrack,
            getEpisodesByAnimeId = mockk<GetEpisodesByAnimeId>(relaxed = true),
            updateEpisode = mockk<UpdateEpisode>(relaxed = true),
            conflictResolver = mockk<TrackSyncConflictResolver>(relaxed = true),
        ).await()

        assertEquals(0, result.unlinkedCount)
        assertEquals(1, result.failures.size)
        assertEquals(malformedResponse.message, result.failures.single().message)
        coVerify(exactly = 0) { deleteTrack.await(any(), any()) }
    }

    @Test
    fun `malformed manga response becomes a failure instead of unlinking`() = runTest {
        val tracker = mockk<BaseTracker>(relaxed = true, moreInterfaces = arrayOf(MangaTracker::class))
        val mangaTracker = tracker as MangaTracker
        val trackerManager = mockk<TrackerManager>()
        val getTracks = mockk<GetMangaTracks>()
        val deleteTrack = mockk<DeleteMangaTrack>(relaxed = true)
        val localTrack = mangaTrack()
        val malformedResponse = MalformedTrackerResponseException("Kavita", "chapter number")

        every { tracker.id } returns localTrack.trackerId
        every { tracker.isLoggedIn } returns true
        every { trackerManager.get(localTrack.trackerId) } returns tracker
        coEvery { getTracks.awaitAll() } returns listOf(localTrack)
        coEvery { mangaTracker.refresh(any()) } throws malformedResponse

        val result = RefreshAllMangaTracks(
            getTracks = getTracks,
            trackerManager = trackerManager,
            insertTrack = mockk<InsertMangaTrack>(relaxed = true),
            deleteTrack = deleteTrack,
            getChaptersByMangaId = mockk<GetChaptersByMangaId>(relaxed = true),
            updateChapter = mockk<UpdateChapter>(relaxed = true),
            conflictResolver = mockk<TrackSyncConflictResolver>(relaxed = true),
        ).await()

        assertEquals(0, result.unlinkedCount)
        assertEquals(1, result.failures.size)
        assertEquals(malformedResponse.message, result.failures.single().message)
        coVerify(exactly = 0) { deleteTrack.await(any(), any()) }
    }

    private fun animeTrack() = DomainAnimeTrack(
        id = 1L,
        animeId = 10L,
        trackerId = 101L,
        remoteId = 100L,
        libraryId = null,
        title = "Anime",
        lastEpisodeSeen = 0.0,
        totalEpisodes = 12L,
        status = 1L,
        score = 0.0,
        remoteUrl = "https://example.com/anime",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )

    private fun mangaTrack() = DomainMangaTrack(
        id = 1L,
        mangaId = 10L,
        trackerId = 8L,
        remoteId = 100L,
        libraryId = null,
        title = "Manga",
        lastChapterRead = 0.0,
        totalChapters = 12L,
        status = 1L,
        score = 0.0,
        remoteUrl = "https://example.com/manga",
        startDate = 0L,
        finishDate = 0L,
        private = false,
    )
}
