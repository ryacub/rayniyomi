package eu.kanade.domain.track.service

import eu.kanade.domain.track.anime.interactor.RefreshAllAnimeTracks
import eu.kanade.domain.track.manga.interactor.RefreshAllMangaTracks
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrackerSyncCoordinatorTest {

    @Test
    fun `enabled sync aggregates manga and anime progress results`() = runTest {
        val preferences = mockk<TrackPreferences>(relaxed = true)
        val refreshManga = mockk<RefreshAllMangaTracks>()
        val refreshAnime = mockk<RefreshAllAnimeTracks>()
        every { preferences.trackerSyncEnabled().get() } returns true
        every { preferences.trackerSyncLastRunMillis().set(any()) } just Runs
        coEvery { refreshManga.await() } returns RefreshAllMangaTracks.Result(
            syncedCount = 2,
            unlinkedCount = 1,
            failures = emptyList(),
        )
        coEvery { refreshAnime.await() } returns RefreshAllAnimeTracks.Result(
            syncedCount = 3,
            unlinkedCount = 2,
            failures = emptyList(),
        )

        val result = TrackerSyncCoordinator(preferences, refreshManga, refreshAnime)
            .await(TrackerSyncTrigger.MANUAL)

        assertEquals(5, result.syncedItems)
        assertEquals(3, result.unlinkedItems)
        assertEquals(TrackerSyncTrigger.MANUAL, result.trigger)
        coVerify(exactly = 1) { refreshManga.await() }
        coVerify(exactly = 1) { refreshAnime.await() }
        verify(exactly = 1) { preferences.trackerSyncLastRunMillis().set(any()) }
    }

    @Test
    fun `disabled sync skips all progress refresh work`() = runTest {
        val preferences = mockk<TrackPreferences>(relaxed = true)
        val refreshManga = mockk<RefreshAllMangaTracks>()
        val refreshAnime = mockk<RefreshAllAnimeTracks>()
        every { preferences.trackerSyncEnabled().get() } returns false

        val result = TrackerSyncCoordinator(preferences, refreshManga, refreshAnime)
            .await(TrackerSyncTrigger.PERIODIC)

        assertEquals(0, result.syncedItems)
        assertEquals(0, result.unlinkedItems)
        assertEquals(TrackerSyncTrigger.PERIODIC, result.trigger)
        coVerify(exactly = 0) { refreshManga.await() }
        coVerify(exactly = 0) { refreshAnime.await() }
        verify(exactly = 0) { preferences.trackerSyncLastRunMillis().set(any()) }
    }
}
