package eu.kanade.tachiyomi.ui.stats.manga

import eu.kanade.presentation.more.stats.StatsScreenState
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.MangaTracker
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
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.history.manga.interactor.GetTotalReadDuration
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.model.MangaTrack

class MangaStatsScreenModelTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() = vt.setUpMain()

    @AfterEach
    fun tearDown() = vt.tearDownMain()

    @Test
    fun `tracked title count comes from one track query`() = runBlocking {
        val libraryManga = listOf(libraryManga(1L), libraryManga(2L), libraryManga(3L))
        val tracks = listOf(
            track(mangaId = 1L, trackerId = LOGGED_IN_TRACKER),
            track(mangaId = 2L, trackerId = LOGGED_OUT_TRACKER),
            track(mangaId = 99L, trackerId = LOGGED_IN_TRACKER),
        )
        val getTracks = mockk<GetMangaTracks>()
        coEvery { getTracks.awaitAll() } returns tracks
        coEvery { getTracks.await(any()) } answers { tracks.filter { it.mangaId == firstArg<Long>() } }

        val model = createModel(libraryManga, getTracks)
        val state = withContext(Dispatchers.Default) {
            withTimeout(5_000) { model.state.first { it is StatsScreenState.SuccessManga } }
        } as StatsScreenState.SuccessManga

        assertEquals(1, state.trackers.trackedTitleCount)
        coVerify(exactly = 0) { getTracks.await(any()) }
    }

    private fun createModel(libraryManga: List<LibraryManga>, getTracks: GetMangaTracks): MangaStatsScreenModel {
        val getLibraryManga = mockk<GetLibraryManga>()
        coEvery { getLibraryManga.await() } returns libraryManga
        val getTotalReadDuration = mockk<GetTotalReadDuration>()
        coEvery { getTotalReadDuration.await() } returns 0L
        val downloadManager = mockk<MangaDownloadManager>()
        every { downloadManager.getDownloadCount() } returns 0
        val tracker = mockk<BaseTracker>(moreInterfaces = arrayOf(MangaTracker::class))
        every { tracker.id } returns LOGGED_IN_TRACKER
        val trackerManager = mockk<TrackerManager>()
        every { trackerManager.loggedInTrackers() } returns listOf(tracker)

        return MangaStatsScreenModel(
            downloadManager = downloadManager,
            getLibraryManga = getLibraryManga,
            getTotalReadDuration = getTotalReadDuration,
            getTracks = getTracks,
            preferences = LibraryPreferences(InMemoryPreferenceStore(emptySequence())),
            trackerManager = trackerManager,
        )
    }

    private fun libraryManga(id: Long) = LibraryManga(
        manga = Manga.create().copy(id = id),
        category = 0L,
        totalChapters = 0L,
        readCount = 0L,
        bookmarkCount = 0L,
        latestUpload = 0L,
        chapterFetchedAt = 0L,
        lastRead = 0L,
    )

    private fun track(mangaId: Long, trackerId: Long) = MangaTrack(
        id = mangaId * 100 + trackerId,
        mangaId = mangaId,
        trackerId = trackerId,
        remoteId = 0L,
        libraryId = null,
        title = "",
        lastChapterRead = 0.0,
        totalChapters = 0L,
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
