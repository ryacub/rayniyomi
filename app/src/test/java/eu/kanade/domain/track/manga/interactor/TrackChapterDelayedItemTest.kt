package eu.kanade.domain.track.manga.interactor

import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore
import eu.kanade.domain.track.manga.store.DelayedMangaTrackingStore.DelayedTrackingItem
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.model.MangaTrack

class TrackChapterDelayedItemTest {

    private val store = mockk<DelayedMangaTrackingStore>(relaxed = true)

    @Test
    fun `a queued chapter that the tracker already reached is removed`() = runTest {
        every { store.getMangaItems() } returns listOf(DelayedTrackingItem(trackId = 7, lastChapterRead = 10f))

        trackChapter(trackedChapter = 12.0).await(mockk(), mangaId = 1, chapterNumber = 10.0)

        verify(exactly = 1) { store.removeMangaItem(7) }
    }

    @Test
    fun `rereading an old chapter keeps a queued chapter that the tracker has not reached`() = runTest {
        every { store.getMangaItems() } returns listOf(DelayedTrackingItem(trackId = 7, lastChapterRead = 15f))

        trackChapter(trackedChapter = 10.0).await(mockk(), mangaId = 1, chapterNumber = 8.0)

        verify(exactly = 0) { store.removeMangaItem(any()) }
    }

    private fun trackChapter(trackedChapter: Double): TrackChapter {
        val track = MangaTrack(
            id = 7,
            mangaId = 1,
            trackerId = 2,
            remoteId = 3,
            libraryId = null,
            title = "Series",
            lastChapterRead = trackedChapter,
            totalChapters = 0,
            status = 0,
            score = 0.0,
            remoteUrl = "",
            startDate = 0,
            finishDate = 0,
            private = false,
        )
        return TrackChapter(
            getTracks = mockk<GetMangaTracks> { coEvery { await(1) } returns listOf(track) },
            trackerManager = mockk<TrackerManager> {
                every { get(2) } returns mockk { every { isLoggedIn } returns true }
            },
            insertTrack = mockk(relaxed = true),
            delayedTrackingStore = store,
        )
    }
}
