package eu.kanade.domain.track

import eu.kanade.domain.track.anime.interactor.RefreshAllAnimeTracks
import eu.kanade.domain.track.interactor.TrackSyncConflictResolver
import eu.kanade.domain.track.manga.interactor.RefreshAllMangaTracks
import eu.kanade.domain.track.service.MediaType
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.MalformedTrackerResponseException
import eu.kanade.tachiyomi.data.track.MangaTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.network.HttpException
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

class MalformedTrackerResponseRoutingTest {

    @ParameterizedTest
    @MethodSource("refreshErrors")
    fun `anime refresh error becomes a failure and keeps the track`(error: Throwable) = runTest {
        val tracker = mockk<BaseTracker>(relaxed = true, moreInterfaces = arrayOf(AnimeTracker::class))
        val animeTracker = tracker as AnimeTracker
        val trackerManager = mockk<TrackerManager>()
        val getTracks = mockk<GetAnimeTracks>()
        val localTrack = animeTrack()

        every { tracker.isLoggedIn } returns true
        every { trackerManager.get(localTrack.trackerId) } returns tracker
        coEvery { getTracks.awaitAll() } returns listOf(localTrack)
        coEvery { animeTracker.refresh(any()) } throws error

        val result = RefreshAllAnimeTracks(
            getTracks = getTracks,
            trackerManager = trackerManager,
            insertTrack = mockk<InsertAnimeTrack>(relaxed = true),
            getEpisodesByAnimeId = mockk<GetEpisodesByAnimeId>(relaxed = true),
            updateEpisode = mockk<UpdateEpisode>(relaxed = true),
            conflictResolver = mockk<TrackSyncConflictResolver>(relaxed = true),
        ).await()

        val failure = result.failures.single()
        assertSame(tracker, failure.tracker)
        assertEquals(MediaType.ANIME, failure.mediaType)
        assertEquals(localTrack.animeId, failure.itemId)
        assertEquals(error.message, failure.message)
    }

    @ParameterizedTest
    @MethodSource("refreshErrors")
    fun `manga refresh error becomes a failure and keeps the track`(error: Throwable) = runTest {
        val tracker = mockk<BaseTracker>(relaxed = true, moreInterfaces = arrayOf(MangaTracker::class))
        val mangaTracker = tracker as MangaTracker
        val trackerManager = mockk<TrackerManager>()
        val getTracks = mockk<GetMangaTracks>()
        val localTrack = mangaTrack()

        every { tracker.isLoggedIn } returns true
        every { trackerManager.get(localTrack.trackerId) } returns tracker
        coEvery { getTracks.awaitAll() } returns listOf(localTrack)
        coEvery { mangaTracker.refresh(any()) } throws error

        val result = RefreshAllMangaTracks(
            getTracks = getTracks,
            trackerManager = trackerManager,
            insertTrack = mockk<InsertMangaTrack>(relaxed = true),
            getChaptersByMangaId = mockk<GetChaptersByMangaId>(relaxed = true),
            updateChapter = mockk<UpdateChapter>(relaxed = true),
            conflictResolver = mockk<TrackSyncConflictResolver>(relaxed = true),
        ).await()

        val failure = result.failures.single()
        assertSame(tracker, failure.tracker)
        assertEquals(MediaType.MANGA, failure.mediaType)
        assertEquals(localTrack.mangaId, failure.itemId)
        assertEquals(error.message, failure.message)
    }

    @Serializable
    private data class RemoteEntry(val id: Long)

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

    companion object {
        @JvmStatic
        fun refreshErrors(): List<Throwable> = listOf(
            MalformedTrackerResponseException("Kavita", "chapter number"),
            checkNotNull(runCatching { Json.decodeFromString<RemoteEntry>("{}") }.exceptionOrNull()),
            HttpException(404),
            Exception("Could not find manga"),
        )
    }
}
