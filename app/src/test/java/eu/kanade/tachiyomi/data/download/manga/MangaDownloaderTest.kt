package eu.kanade.tachiyomi.data.download.manga

import com.hippo.unifile.UniFile
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.notification.NotificationHandler
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.storage.DiskUtil
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.source.manga.MangaSourceGateway
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

class MangaDownloaderTest {

    private lateinit var downloader: MangaDownloader
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val downloadPreferences = mockk<DownloadPreferences>(relaxed = true)
        every { downloadPreferences.pageDownloadConcurrency().get() } returns 2

        val sourcePreferences = mockk<SourcePreferences>(relaxed = true)
        every { sourcePreferences.dataSaverDownloader().get() } returns false

        Injekt.addSingleton<MangaSourceManager>(mockk(relaxed = true))
        Injekt.addSingleton<Json>(Json { ignoreUnknownKeys = true })
        Injekt.addSingleton<GetManga>(mockk(relaxed = true))
        Injekt.addSingleton<GetChapter>(mockk(relaxed = true))

        mockkObject(DiskUtil)
        every { DiskUtil.getAvailableStorageSpace(any<UniFile>()) } returns 1_000_000_000L

        mockkObject(NotificationHandler)
        every { NotificationHandler.openDownloadManagerPendingActivity(any()) } returns mockk(relaxed = true)

        downloader = MangaDownloader(
            context = mockk(relaxed = true),
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
        unmockkObject(NotificationHandler)
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
        imageUrlDeferred.complete("unused")
        downloadJob.join()

        assertEquals(Page.State.LOAD_PAGE, page.status)
    }

    @Test
    fun `source failure during image url fetch marks only that page as error`() = runTest {
        val page = Page(0)
        val download = MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test"),
            chapter = Chapter.create().copy(id = 1L, name = "Ch1"),
        ).apply { pages = listOf(page) }

        mockkObject(MangaSourceGateway)
        coEvery { MangaSourceGateway.imageUrl(any(), any()) } throws RuntimeException("boom")

        downloader.downloadChapter(download)

        assertEquals(Page.State.ERROR, page.status)
    }
}
