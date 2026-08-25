package eu.kanade.tachiyomi.data.download.anime

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.anime.strategy.DownloadStrategySelector
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.ui.player.loader.EpisodeLoader
import eu.kanade.tachiyomi.util.storage.DiskUtil
import eu.kanade.tachiyomi.util.system.notificationBuilder
import eu.kanade.tachiyomi.util.system.notify
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.FileNotFoundException
import java.io.IOException

class AnimeDownloaderTest {

    private lateinit var downloader: AnimeDownloader
    private val testDispatcher = StandardTestDispatcher()
    private val strategySelector = mockk<DownloadStrategySelector>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val downloadPreferences = mockk<DownloadPreferences>(relaxed = true)
        every { downloadPreferences.useExternalDownloader().get() } returns false
        every { downloadPreferences.multiThreadDownloads().get() } returns false
        every { downloadPreferences.numberOfDownloads().get() } returns 1
        every { downloadPreferences.multiThreadConnections().get() } returns 2

        Injekt.addSingleton<DownloadPreferences>(downloadPreferences)
        Injekt.addSingleton<AnimeSourceManager>(mockk(relaxed = true))
        Injekt.addSingleton<Json>(Json { ignoreUnknownKeys = true })
        Injekt.addSingleton<GetAnime>(mockk(relaxed = true))
        Injekt.addSingleton<GetEpisode>(mockk(relaxed = true))

        mockkObject(DiskUtil)
        every { DiskUtil.getAvailableStorageSpace(any<UniFile>()) } returns 1_000_000_000L

        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        mockkStatic("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt")
        mockkObject(AnimeDownloadJob.Companion)
        every { AnimeDownloadJob.stop(any()) } just runs
        mockkConstructor(AnimeDownloadNotifier::class)
        mockkObject(NotificationHandler)
        every { anyConstructed<AnimeDownloadNotifier>().onError(any(), any(), any(), any()) } just runs
        every { anyConstructed<AnimeDownloadNotifier>().onWarning(any(), any(), any(), any()) } just runs
        every { anyConstructed<AnimeDownloadNotifier>().onQueueStatusSummary(any()) } just runs
        every { anyConstructed<AnimeDownloadNotifier>().onProgressChange(any()) } just runs
        every { anyConstructed<AnimeDownloadNotifier>().onPaused() } just runs
        every { anyConstructed<AnimeDownloadNotifier>().onComplete() } just runs
        every { anyConstructed<AnimeDownloadNotifier>().dismissProgress() } just runs
        every { NotificationHandler.openDownloadManagerPendingActivity(any()) } returns mockk(relaxed = true)
        mockkObject(NotificationReceiver.Companion)
        every { NotificationReceiver.openAnimeEntryPendingActivity(any(), any()) } returns mockk(relaxed = true)

        val context = mockk<Context>(relaxed = true)
        val notificationBuilder = mockk<NotificationCompat.Builder>(relaxed = true)
        every { context.notificationBuilder(any(), any()) } returns notificationBuilder
        every { context.notify(any<Int>(), any<Notification>()) } just runs
        every { context.stringResource(any()) } returns "mocked"

        downloader = AnimeDownloader(
            context = context,
            provider = mockk(relaxed = true),
            cache = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            stateStore = mockk(relaxed = true),
            strategySelector = strategySelector,
            multiThreadDownloader = mockk(relaxed = true),
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        unmockkStatic("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt")
        unmockkObject(NotificationHandler)
        unmockkConstructor(AnimeDownloadNotifier::class)
        unmockkObject(AnimeDownloadJob.Companion)
        unmockkObject(NotificationReceiver.Companion)
        unmockkObject(DiskUtil)
        unmockkObject(EpisodeLoader.Companion)
        Dispatchers.resetMain()
    }

    private fun testDownload(video: Video? = null): AnimeDownload =
        AnimeDownload(
            source = mockk<AnimeHttpSource>(relaxed = true),
            anime = Anime.create().copy(id = 1L, title = "Test"),
            episode = Episode.create().copy(id = 1L, name = "Ch1"),
        ).apply { this.video = video }

    private fun mockHosters(block: suspend () -> Nothing) {
        mockkObject(EpisodeLoader.Companion)
        coEvery { EpisodeLoader.getHosters(any(), any(), any()) } coAnswers { block() }
    }

    @Test
    fun `wrapped cancellation cause reaches error reporting`() = runTest {
        val download = testDownload()

        mockHosters { throw WrappedCancellationException(RuntimeException("HTTP error 522")) }

        downloader.launchDownloadJobForTest(this, download).join()

        assertEquals(AnimeDownload.State.ERROR, download.status)
        assertEquals("RuntimeException", download.lastErrorCode)
        assertTrue(download.lastErrorReason.orEmpty().contains("HTTP error 522"))
        verify(exactly = 1) {
            anyConstructed<AnimeDownloadNotifier>().onError(
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `genuine scope cancellation skips error reporting`() = runTest {
        val download = testDownload()

        val hostersStarted = CompletableDeferred<Unit>()
        val releaseHosters = CompletableDeferred<Unit>()
        mockHosters {
            hostersStarted.complete(Unit)
            releaseHosters.await()
            throw CancellationException("scope cancelled")
        }

        val downloadJob = downloader.launchDownloadJobForTest(this, download)
        hostersStarted.await()
        downloadJob.cancel()
        releaseHosters.complete(Unit)
        downloadJob.join()

        assertNotEquals(AnimeDownload.State.ERROR, download.status)
        assertNull(download.lastErrorCode)
        assertNull(download.lastErrorReason)
    }

    @Test
    fun `cancellation shaped failure while the scope is active reports an error`() = runTest {
        val download = testDownload()

        mockHosters { throw CancellationException("cancelled inside source") }

        downloader.launchDownloadJobForTest(this, download).join()

        assertEquals(AnimeDownload.State.ERROR, download.status)
        assertEquals("CancellationException", download.lastErrorCode)
        verify(exactly = 1) {
            anyConstructed<AnimeDownloadNotifier>().onError(
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `cancellation wrapped failure with a null message falls back to the class name`() = runTest {
        val download = testDownload()

        mockHosters { throw WrappedCancellationException(RuntimeException()) }

        downloader.launchDownloadJobForTest(this, download).join()

        assertEquals("RuntimeException", download.lastErrorCode)
        assertEquals("RuntimeException", download.lastErrorReason)
    }

    @Test
    fun `retry exhaustion reason reflects the last exception`() = runTest {
        downloader.retryBackoffMillis = 0L
        val video = mockk<Video>(relaxed = true)
        every { video.videoUrl } returns "url"
        every { video.headers } returns null
        val download = testDownload(video)

        var attempts = 0
        coEvery {
            strategySelector.selectStrategy(any(), any(), any(), any())
        } coAnswers {
            attempts++
            throw IOException("HTTP error 504")
        }

        downloader.launchDownloadJobForTest(this, download).join()

        assertTrue((download.lastErrorReason ?: "").startsWith("IOException"))
        assertTrue((download.lastErrorReason ?: "").contains("HTTP error 504"))
        assertNotEquals("Network retries exhausted", download.lastErrorReason)
        assertEquals(4, attempts)
        verify(exactly = 1) {
            anyConstructed<AnimeDownloadNotifier>().onError(
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `permission denied failure fails fast without retries`() = runTest {
        downloader.retryBackoffMillis = 0L
        val video = mockk<Video>(relaxed = true)
        every { video.videoUrl } returns "url"
        every { video.headers } returns null
        val download = testDownload(video)

        var attempts = 0
        coEvery {
            strategySelector.selectStrategy(any(), any(), any(), any())
        } coAnswers {
            attempts++
            throw FileNotFoundException(
                "/data/data/files/downloads/Test/Ch1_tmp/001.tmp: open failed: EPERM (Operation not permitted)",
            )
        }

        downloader.launchDownloadJobForTest(this, download).join()

        assertEquals(1, attempts)
        assertEquals(AnimeDownload.State.ERROR, download.status)
        val reason = download.lastErrorReason.orEmpty()
        assertTrue(reason.contains("EPERM") || reason.contains("Permission denied", ignoreCase = true))
    }
}


/**
 * A cancellation exception that carries a real failure as its cause.
 */
private class WrappedCancellationException(cause: Throwable) : CancellationException("wrapped") {
    init {
        initCause(cause)
    }
}
