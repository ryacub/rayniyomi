package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.storage.service.StorageManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class MangaDownloadCacheRenewTest {

    @TempDir
    lateinit var cacheDir: File

    private val scanStarted = CountDownLatch(1)
    private val releaseScan = CountDownLatch(1)

    @Volatile
    private var chapterDirNames = listOf(EXISTING, OTHER)

    private val source = mockk<HttpSource> { every { id } returns SOURCE_ID }
    private val manga = Manga.create().copy(id = 1L, source = SOURCE_ID, title = MANGA_TITLE)

    private val mangaDir = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns MANGA_TITLE
        every { listFiles() } answers {
            scanStarted.countDown()
            releaseScan.await(10, TimeUnit.SECONDS)
            chapterDirNames.map(::chapterDir).toTypedArray()
        }
    }

    private val sourceDir = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns SOURCE_DIR_NAME
        every { listFiles() } returns arrayOf(mangaDir)
    }

    private val rootDir = mockk<UniFile>(relaxed = true) {
        every { listFiles() } returns arrayOf(sourceDir)
    }

    private val cache by lazy {
        MangaDownloadCache(
            context = mockk<Context> { every { cacheDir } returns this@MangaDownloadCacheRenewTest.cacheDir },
            provider = mockk {
                every { getSourceDirName(any()) } returns SOURCE_DIR_NAME
                every { getMangaDirName(any()) } answers { firstArg() }
                every { getValidChapterDirNames(any(), any()) } answers { listOf(firstArg()) }
                every { findSourceDir(any()) } returns sourceDir
            },
            sourceManager = mockk<MangaSourceManager> {
                every { isInitialized } returns MutableStateFlow(true)
                every { getOnlineSources() } returns listOf(source)
                every { getStubSources() } returns emptyList()
                every { get(SOURCE_ID) } returns source
            },
            extensionManager = mockk<MangaExtensionManager> {
                every { isInitialized } returns MutableStateFlow(true)
            },
            storageManager = mockk<StorageManager> {
                every { getDownloadsDirectory() } returns rootDir
                every { changes } returns MutableSharedFlow()
            },
        )
    }

    @AfterEach
    fun tearDown() {
        releaseScan.countDown()
        AppGraphHolder.graphOrNull = null
        unmockkStatic(UniFile::class)
    }

    @Test
    fun `adding a chapter does not wait for a running cache scan`() = runBlocking<Unit> {
        startScan()

        val added = withTimeoutOrNull(2.seconds) {
            cache.addChapter(ADDED, mangaDir, manga)
        }

        added.shouldNotBeNull()
    }

    @Test
    fun `a chapter added during a cache scan stays in the cache after the scan`() = runBlocking<Unit> {
        startScan()

        runDuringScan { cache.addChapter(ADDED, mangaDir, manga) }
        awaitDownloaded(OTHER)

        isDownloaded(ADDED) shouldBe true
    }

    @Test
    fun `a chapter removed during a cache scan stays removed after the scan`() = runBlocking<Unit> {
        startScan()

        runDuringScan { cache.removeChapter(chapter(EXISTING), manga) }
        awaitDownloaded(OTHER)

        isDownloaded(EXISTING) shouldBe false
    }

    @Test
    fun `an invalidation during a cache scan rescans the downloads folder`() {
        startScan()

        chapterDirNames = listOf(EXISTING, ADDED)
        cache.invalidateCache()
        releaseScan.countDown()

        awaitDownloaded(ADDED)
    }

    @Test
    fun `an invalidation while the disk index loads rescans the downloads folder`() {
        writeDiskIndex(listOf(STALE))
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        restoreIndexFoldersAfter(loadStarted, releaseLoad)
        releaseScan.countDown()

        isDownloaded(EXISTING) shouldBe false
        loadStarted.await(10, TimeUnit.SECONDS) shouldBe true
        cache.invalidateCache()
        releaseLoad.countDown()

        awaitDownloaded(EXISTING)
    }

    private fun startScan() {
        cache.invalidateCache()
        scanStarted.await(10, TimeUnit.SECONDS) shouldBe true
    }

    private fun awaitDownloaded(chapterName: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!isDownloaded(chapterName)) {
            check(System.nanoTime() < deadline) { "The cache scan did not finish" }
            Thread.sleep(10)
        }
    }

    private fun isDownloaded(chapterName: String) =
        cache.isChapterDownloaded(chapterName, null, MANGA_TITLE, SOURCE_ID, skipCache = false)

    private suspend fun CoroutineScope.runDuringScan(mutation: suspend () -> Unit) {
        val job = launch(Dispatchers.IO) { mutation() }
        withTimeoutOrNull(2.seconds) { job.join() }
        releaseScan.countDown()
        job.join()
    }

    private fun chapter(chapterName: String) = Chapter.create().copy(name = chapterName, scanlator = null)

    private fun chapterDir(dirName: String) = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns dirName
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeDiskIndex(chapterDirNames: List<String>) {
        val index = IndexRoot(
            sourceDirs = mapOf(
                SOURCE_ID to IndexSource(
                    mangaDirs = mapOf(
                        MANGA_TITLE to IndexManga(dir = FOLDER_URI, chapterDirs = chapterDirNames.toSet()),
                    ),
                ),
            ),
        )
        File(cacheDir, "dl_index_cache_v3").writeBytes(ProtoBuf.encodeToByteArray(index))
    }

    private fun restoreIndexFoldersAfter(loadStarted: CountDownLatch, releaseLoad: CountDownLatch) {
        AppGraphHolder.graphOrNull = mockk<AppGraph> { every { application } returns mockk() }
        mockkStatic(UniFile::class)
        every { UniFile.fromUri(any(), any()) } answers {
            loadStarted.countDown()
            releaseLoad.await(10, TimeUnit.SECONDS)
            mangaDir
        }
    }

    @Serializable
    private class IndexRoot(val dir: String? = null, val sourceDirs: Map<Long, IndexSource> = mapOf())

    @Serializable
    private class IndexSource(val dir: String? = null, val mangaDirs: Map<String, IndexManga> = mapOf())

    @Serializable
    private class IndexManga(val dir: String? = null, val chapterDirs: Set<String> = setOf())

    private companion object {
        const val SOURCE_ID = 1L
        const val SOURCE_DIR_NAME = "Source"
        const val MANGA_TITLE = "Title"
        const val EXISTING = "Existing"
        const val OTHER = "Other"
        const val ADDED = "Added"
        const val STALE = "Stale"
        const val FOLDER_URI = "content://folder"
    }
}
