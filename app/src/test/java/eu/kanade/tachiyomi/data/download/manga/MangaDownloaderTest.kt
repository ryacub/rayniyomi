package eu.kanade.tachiyomi.data.download.manga

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import com.hippo.unifile.UniFile
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.source.model.Page
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.data.source.manga.MangaSourceGateway
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.FileNotFoundException
import java.io.IOException

class MangaDownloaderTest {

    private lateinit var downloader: MangaDownloader
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val downloadPreferences = mockk<DownloadPreferences>(relaxed = true)
        every { downloadPreferences.pageDownloadConcurrency().get() } returns 2
        every { downloadPreferences.downloadSpeedLimit().get() } returns 0

        val sourcePreferences = mockk<SourcePreferences>(relaxed = true)
        every { sourcePreferences.dataSaverDownloader().get() } returns false

        Injekt.addSingleton<MangaSourceManager>(mockk(relaxed = true))
        Injekt.addSingleton<Json>(Json { ignoreUnknownKeys = true })
        Injekt.addSingleton<GetManga>(mockk(relaxed = true))
        Injekt.addSingleton<GetChapter>(mockk(relaxed = true))

        mockkObject(DiskUtil)
        every { DiskUtil.getAvailableStorageSpace(any<UniFile>()) } returns 1_000_000_000L

        mockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        mockkStatic("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt")
        mockkObject(MangaDownloadJob.Companion)
        every { MangaDownloadJob.stop(any()) } just runs
        mockkConstructor(MangaDownloadNotifier::class)
        mockkObject(NotificationHandler)
        every { anyConstructed<MangaDownloadNotifier>().onError(any(), any(), any(), any()) } just runs
        every { anyConstructed<MangaDownloadNotifier>().onQueueStatusSummary(any()) } just runs
        every { anyConstructed<MangaDownloadNotifier>().onPaused() } just runs
        every { anyConstructed<MangaDownloadNotifier>().onComplete() } just runs
        every { anyConstructed<MangaDownloadNotifier>().onProgressChange(any()) } just runs
        every { NotificationHandler.openDownloadManagerPendingActivity(any()) } returns mockk(relaxed = true)
        mockkObject(NotificationReceiver.Companion)
        every { NotificationReceiver.openMangaEntryPendingActivity(any(), any()) } returns mockk(relaxed = true)

        val context = mockk<Context>(relaxed = true)
        val notificationBuilder = mockk<NotificationCompat.Builder>(relaxed = true)
        every { context.notificationBuilder(any(), any()) } returns notificationBuilder
        every { context.notify(any<Int>(), any<Notification>()) } just runs
        every { context.stringResource(any()) } returns "mocked"
        every { context.stringResource(any(), *anyVararg()) } returns "mocked"

        downloader = MangaDownloader(
            context = context,
            provider = mockk(relaxed = true),
            cache = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            chapterCache = mockk(relaxed = true),
            downloadPreferences = downloadPreferences,
            xml = mockk(relaxed = true),
            getCategories = mockk(relaxed = true),
            getMangaTracks = mockk(relaxed = true),
            sourcePreferences = sourcePreferences,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("tachiyomi.core.common.i18n.LocalizeKt")
        unmockkStatic("eu.kanade.tachiyomi.util.system.NotificationExtensionsKt")
        unmockkObject(NotificationHandler)
        unmockkConstructor(MangaDownloadNotifier::class)
        unmockkObject(MangaDownloadJob.Companion)
        unmockkObject(NotificationReceiver.Companion)
        unmockkObject(DiskUtil)
        unmockkObject(MangaSourceGateway)
        Dispatchers.resetMain()
    }

    @Test
    fun `cancellation during image url fetch does not mark page error`() = runTest {
        val page = Page(0)
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        ).apply { pages = listOf(page) }

        val imageUrlStarted = CompletableDeferred<Unit>()
        val imageUrlDeferred = CompletableDeferred<String>()
        mockkObject(MangaSourceGateway)
        coEvery { MangaSourceGateway.imageUrl(any(), any()) } coAnswers {
            imageUrlStarted.complete(Unit)
            imageUrlDeferred.await()
        }

        val downloadJob = launch {
            downloader.downloadChapter(download)
        }

        imageUrlStarted.await()
        downloadJob.cancel()
        imageUrlDeferred.cancel()
        downloadJob.join()

        assertEquals(Page.State.LOAD_PAGE, page.status)
    }

    @Test
    fun `source failure during image url fetch marks only that page as error`() = runTest {
        val page = Page(0)
        val unaffectedPage = Page(1, url = "url", imageUrl = "image")
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        ).apply { pages = listOf(page, unaffectedPage) }
        downloader.retryBackoffMillis = 0L

        val imageUrlStarted = CompletableDeferred<Unit>()
        val releaseImageUrl = CompletableDeferred<Unit>()
        val imageStarted = CompletableDeferred<Unit>()
        val releaseImage = CompletableDeferred<Unit>()
        mockkObject(MangaSourceGateway)
        coEvery { MangaSourceGateway.imageUrl(any(), any()) } coAnswers {
            imageUrlStarted.complete(Unit)
            releaseImageUrl.await()
            throw RuntimeException("boom")
        }
        // The unaffected page reaches the image fetch. Serve a response whose body
        // cannot be read, so its status stays DOWNLOAD_IMAGE and never becomes ERROR.
        val response = mockk<Response>(relaxed = true)
        val body = mockk<ResponseBody>()
        every { response.body } returns body
        every { body.source() } throws IOException("unaffected page body")
        coEvery { MangaSourceGateway.image(any(), any(), any()) } coAnswers {
            imageStarted.complete(Unit)
            releaseImage.await()
            response
        }

        val downloadJob = launch {
            downloader.downloadChapter(download)
        }

        imageUrlStarted.await()
        releaseImageUrl.complete(Unit)
        imageStarted.await()
        releaseImage.complete(Unit)
        downloadJob.join()

        withTimeout(5_000) {
            page.statusFlow.first { it == Page.State.ERROR }
        }
        assertEquals(Page.State.ERROR, page.status)
        assertNotEquals(Page.State.ERROR, unaffectedPage.status)
    }

    @Test
    fun `wrapped cancellation cause reaches error reporting`() = runTest {
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        )

        mockkObject(MangaSourceGateway)
        coEvery { MangaSourceGateway.pages(any(), any()) } coAnswers {
            throw WrappedCancellationException(RuntimeException("HTTP error 522"))
        }

        downloader.launchDownloadJobForTest(this, download).join()

        assertEquals(MangaDownload.State.ERROR, download.status)
        assertEquals("RuntimeException", download.lastErrorCode)
        assertTrue(download.lastErrorReason.orEmpty().contains("HTTP error 522"))
        verify(exactly = 1) {
            anyConstructed<MangaDownloadNotifier>().onError(
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `genuine scope cancellation skips error reporting`() = runTest {
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        )

        val pagesStarted = CompletableDeferred<Unit>()
        val releasePages = CompletableDeferred<Unit>()
        mockkObject(MangaSourceGateway)
        coEvery { MangaSourceGateway.pages(any(), any()) } coAnswers {
            pagesStarted.complete(Unit)
            releasePages.await()
            throw CancellationException("scope cancelled")
        }

        val downloadJob = downloader.launchDownloadJobForTest(this, download)
        pagesStarted.await()
        downloadJob.cancel()
        releasePages.complete(Unit)
        downloadJob.join()

        assertNotEquals(MangaDownload.State.ERROR, download.status)
        assertNull(download.lastErrorCode)
        assertNull(download.lastErrorReason)
    }

    @Test
    fun `cancellation shaped failure while the scope is active reports an error`() = runTest {
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        )

        mockkObject(MangaSourceGateway)
        coEvery { MangaSourceGateway.pages(any(), any()) } throws CancellationException("cancelled inside source")

        downloader.launchDownloadJobForTest(this, download).join()

        assertEquals(MangaDownload.State.ERROR, download.status)
        assertEquals("CancellationException", download.lastErrorCode)
        verify(exactly = 1) {
            anyConstructed<MangaDownloadNotifier>().onError(
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `cancellation wrapped failure with a null message falls back to the class name`() = runTest {
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        )

        mockkObject(MangaSourceGateway)
        coEvery { MangaSourceGateway.pages(any(), any()) } throws WrappedCancellationException(RuntimeException())

        downloader.launchDownloadJobForTest(this, download).join()

        assertEquals("RuntimeException", download.lastErrorCode)
        assertEquals("RuntimeException", download.lastErrorReason)
    }

    @Test
    fun `retry exhaustion reason reflects the last exception`() = runTest {
        downloader.retryBackoffMillis = 0L
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        ).apply {
            pages = listOf(Page(0, url = "url", imageUrl = "image"))
        }

        var imageCalls = 0
        mockkObject(MangaSourceGateway)
        coEvery { MangaSourceGateway.image(any(), any(), any()) } coAnswers {
            imageCalls++
            throw IOException("HTTP error 504")
        }

        downloader.launchDownloadJobForTest(this, download).join()

        assertTrue((download.lastErrorReason ?: "").startsWith("IOException"))
        assertTrue((download.lastErrorReason ?: "").contains("HTTP error 504"))
        assertNotEquals("Network retries exhausted", download.lastErrorReason)
        assertEquals(4, imageCalls)
    }

    @Test
    fun `permission denied failure fails fast without retries`() = runTest {
        downloader.retryBackoffMillis = 0L
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        ).apply {
            pages = listOf(Page(0, url = "url", imageUrl = "image"))
        }

        var imageCalls = 0
        mockkObject(MangaSourceGateway)
        coEvery { MangaSourceGateway.image(any(), any(), any()) } coAnswers {
            imageCalls++
            throw FileNotFoundException(
                "/data/data/files/downloads/Test/Ch1_tmp/001.tmp: open failed: EPERM (Operation not permitted)",
            )
        }

        downloader.launchDownloadJobForTest(this, download).join()

        assertEquals(1, imageCalls)
        assertEquals(MangaDownload.State.ERROR, download.status)
        val reason = download.lastErrorReason.orEmpty()
        assertTrue(reason.contains("EPERM") || reason.contains("Permission denied", ignoreCase = true))
    }

    @Test
    fun `cancellation during image download does not mark page error`() = runTest {
        val page = Page(0, url = "url", imageUrl = "image")
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        ).apply {
            pages = listOf(page)
        }

        val imageStarted = CompletableDeferred<Unit>()
        val releaseImage = CompletableDeferred<Unit>()
        mockkObject(MangaSourceGateway)
        coEvery { MangaSourceGateway.image(any(), any(), any()) } coAnswers {
            imageStarted.complete(Unit)
            releaseImage.await()
            throw CancellationException("scope cancelled")
        }

        val downloadJob = downloader.launchDownloadJobForTest(this, download)
        imageStarted.await()
        downloadJob.cancel()
        releaseImage.complete(Unit)
        downloadJob.join()

        assertNotEquals(Page.State.ERROR, page.status)
        assertNotEquals(MangaDownload.State.ERROR, download.status)
        assertNull(download.lastErrorCode)
        verify(exactly = 0) {
            anyConstructed<MangaDownloadNotifier>().onError(
                any(),
                any(),
                any(),
                any(),
            )
        }
    }

    @Test
    fun `pre-flight low storage sends a warning and never an error`() = runTest {
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        )

        every { DiskUtil.getAvailableStorageSpace(any<UniFile>()) } returns 100L * 1024 * 1024
        every { anyConstructed<MangaDownloadNotifier>().onWarning(any(), any(), any(), any()) } just runs

        downloader.downloadChapter(download)

        assertEquals("LOW_STORAGE", download.lastErrorCode)
        assertEquals(DownloadDisplayStatus.PAUSED_LOW_STORAGE, download.displayStatus)
        verify(exactly = 0) {
            anyConstructed<MangaDownloadNotifier>().onError(any(), any(), any(), any())
        }
        verify(exactly = 1) {
            anyConstructed<MangaDownloadNotifier>().onWarning(any(), null, null, 1L)
        }
    }

    @Test
    fun `mid download low storage warns with the localized text and keeps the raw reason`() = runTest {
        downloader.retryBackoffMillis = 0L
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        ).apply {
            pages = listOf(Page(0, url = "url", imageUrl = "image"))
        }

        val response = mockk<Response>(relaxed = true)
        val body = mockk<ResponseBody>()
        every { response.body } returns body
        every { body.source() } throws IOException("No space left on device")
        mockkObject(MangaSourceGateway)
        coEvery { MangaSourceGateway.image(any(), any(), any()) } returns response
        every { anyConstructed<MangaDownloadNotifier>().onWarning(any(), any(), any(), any()) } just runs

        downloader.launchDownloadJobForTest(this, download).join()

        assertEquals("LOW_STORAGE", download.lastErrorCode)
        assertEquals("No space left on device", download.lastErrorReason)
        assertEquals(DownloadDisplayStatus.PAUSED_LOW_STORAGE, download.displayStatus)
        verify(exactly = 0) {
            anyConstructed<MangaDownloadNotifier>().onWarning("No space left on device", any(), any(), any())
        }
        verify(exactly = 1) {
            anyConstructed<MangaDownloadNotifier>().onWarning("mocked", null, null, 1L)
        }
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
