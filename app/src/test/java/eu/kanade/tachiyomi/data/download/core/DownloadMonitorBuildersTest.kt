package eu.kanade.tachiyomi.data.download.core

import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.data.download.model.DownloadStatusTracker
import eu.kanade.tachiyomi.data.download.model.TestStatusSnapshot
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.episode.model.Episode

class DownloadMonitorBuildersTest {

    private companion object {
        const val TIMEOUT_MS = 30_000L
    }

    @Test
    fun `progress monitor updates progress state and reports while running`() = runTest {
        val download = TestStatusSnapshot(isRunningTransfer = true, retryAttempt = 3)
        val progress = MutableStateFlow(0)
        var notified = 0

        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                listOf(
                    DownloadMonitorBuilders.progressMonitor(download, progress) { notified++ },
                ),
            ) {
                progress.value = 50
                delay(100)
            }
        }

        assertEquals(DownloadDisplayStatus.DOWNLOADING, download.displayStatus)
        assertTrue(download.lastProgressAt > 0)
        assertEquals(0, download.retryAttempt)
        assertTrue(notified >= 1)
    }

    @Test
    fun `progress monitor ignores progress when download is not running`() = runTest {
        val download = TestStatusSnapshot(isRunningTransfer = false)
        val progress = MutableStateFlow(0)
        var notified = 0

        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                listOf(
                    DownloadMonitorBuilders.progressMonitor(download, progress) { notified++ },
                ),
            ) {
                progress.value = 50
                delay(100)
            }
        }

        assertEquals(DownloadDisplayStatus.PREPARING, download.displayStatus)
        assertEquals(0L, download.lastProgressAt)
        assertEquals(0, download.retryAttempt)
        assertEquals(0, notified)
    }

    @Test
    fun `stall monitor marks STALLED and reports after the threshold`() = runTest {
        val download = TestStatusSnapshot(
            isRunningTransfer = true,
            displayStatus = DownloadDisplayStatus.DOWNLOADING,
            lastProgressAt = 1L,
        )
        val stalled = CompletableDeferred<Unit>()

        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                listOf(
                    DownloadMonitorBuilders.stallMonitor(download) { stalled.complete(Unit) },
                ),
            ) {
                stalled.await()
            }
        }

        assertEquals(DownloadDisplayStatus.STALLED, download.displayStatus)
        assertTrue(stalled.isCompleted)
    }

    @Test
    fun `stall monitor does not mark STALLED before the threshold`() = runTest {
        val download = TestStatusSnapshot(
            isRunningTransfer = true,
            displayStatus = DownloadDisplayStatus.DOWNLOADING,
            lastProgressAt = System.currentTimeMillis() + DownloadStatusTracker.STALL_THRESHOLD_MS,
        )
        var notified = 0

        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                listOf(
                    DownloadMonitorBuilders.stallMonitor(download) { notified++ },
                ),
            ) {
                delay(2_000)
            }
        }

        assertEquals(DownloadDisplayStatus.DOWNLOADING, download.displayStatus)
        assertEquals(0, notified)
    }

    @Test
    fun `monitors runs both progress and stall monitors`() = runTest {
        val download = TestStatusSnapshot(
            isRunningTransfer = true,
            displayStatus = DownloadDisplayStatus.DOWNLOADING,
        )
        val progress = MutableStateFlow(0)
        var notified = 0

        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                DownloadMonitorBuilders.monitors(download, progress) { notified++ },
            ) {
                progress.value = 50
                delay(100)
            }
        }

        assertTrue(download.lastProgressAt > 0)
        assertEquals(0, download.retryAttempt)
        assertTrue(notified >= 1)
    }

    @Test
    fun `monitors returns progress and stall monitors`() = runTest {
        val download = TestStatusSnapshot(isRunningTransfer = true)

        val monitors = DownloadMonitorBuilders.monitors(download, flowOf(0)) { }

        assertEquals(2, monitors.size)
        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(monitors) { }
        }
    }

    @Test
    fun `progress monitor updates a MangaDownload`() = runTest {
        val source = mockk<HttpSource>(relaxed = true)
        val manga = Manga.create().copy(id = 1L, source = 100L)
        val chapter = Chapter.create().copy(id = 11L, mangaId = 1L)
        val download = MangaDownload(source, manga, chapter).apply {
            status = MangaDownload.State.DOWNLOADING
            retryAttempt = 3
        }
        val reported = CompletableDeferred<Unit>()

        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                DownloadMonitorBuilders.monitors(download, download.progressFlow) {
                    reported.complete(Unit)
                },
            ) {
                delay(100)
                reported.await()
            }
        }

        assertEquals(DownloadDisplayStatus.DOWNLOADING, download.displayStatus)
        assertTrue(download.lastProgressAt > 0)
        assertEquals(0, download.retryAttempt)
    }

    @Test
    fun `progress monitor updates an AnimeDownload`() = runTest {
        val source = mockk<AnimeHttpSource>(relaxed = true)
        val anime = Anime.create().copy(id = 1L, source = 100L)
        val episode = Episode.create().copy(id = 11L, animeId = 1L)
        val download = AnimeDownload(source, anime, episode).apply {
            status = AnimeDownload.State.DOWNLOADING
            retryAttempt = 3
            progress = 50
        }
        val reported = CompletableDeferred<Unit>()

        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                DownloadMonitorBuilders.monitors(download, download.progressFlow) {
                    reported.complete(Unit)
                },
            ) {
                reported.await()
            }
        }

        assertEquals(DownloadDisplayStatus.DOWNLOADING, download.displayStatus)
        assertTrue(download.lastProgressAt > 0)
        assertEquals(0, download.retryAttempt)
    }

    @Test
    fun `stall monitor marks a MangaDownload STALLED`() = runTest {
        val source = mockk<HttpSource>(relaxed = true)
        val manga = Manga.create().copy(id = 1L, source = 100L)
        val chapter = Chapter.create().copy(id = 11L, mangaId = 1L)
        val download = MangaDownload(source, manga, chapter).apply {
            status = MangaDownload.State.DOWNLOADING
            displayStatus = DownloadDisplayStatus.DOWNLOADING
            lastProgressAt = 1L
            retryAttempt = 2
        }
        val stalled = CompletableDeferred<Unit>()

        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                listOf(DownloadMonitorBuilders.stallMonitor(download) { stalled.complete(Unit) }),
            ) {
                stalled.await()
            }
        }

        assertEquals(DownloadDisplayStatus.STALLED, download.displayStatus)
        assertEquals(2, download.retryAttempt)
    }

    @Test
    fun `stall monitor marks an AnimeDownload STALLED`() = runTest {
        val source = mockk<AnimeHttpSource>(relaxed = true)
        val anime = Anime.create().copy(id = 1L, source = 100L)
        val episode = Episode.create().copy(id = 11L, animeId = 1L)
        val download = AnimeDownload(source, anime, episode).apply {
            status = AnimeDownload.State.DOWNLOADING
            displayStatus = DownloadDisplayStatus.DOWNLOADING
            lastProgressAt = 1L
            retryAttempt = 2
        }
        val stalled = CompletableDeferred<Unit>()

        withTimeoutOrNull(TIMEOUT_MS) {
            DownloadMonitors.withMonitors(
                listOf(DownloadMonitorBuilders.stallMonitor(download) { stalled.complete(Unit) }),
            ) {
                stalled.await()
            }
        }

        assertEquals(DownloadDisplayStatus.STALLED, download.displayStatus)
        assertEquals(2, download.retryAttempt)
    }
}
