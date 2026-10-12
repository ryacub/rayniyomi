package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MangaDownloadCacheSizeTest {

    @TempDir
    lateinit var cacheDir: File

    private val lengthReads = AtomicInteger()
    private var blockFirstLength: CountDownLatch? = null
    private val firstLengthBlocked = AtomicBoolean(false)
    private val walkStarted = CountDownLatch(1)

    private val source = mockk<HttpSource> { every { id } returns SOURCE_ID }
    private val manga = Manga.create().copy(id = 1L, source = SOURCE_ID, title = MANGA_TITLE)

    private val chapterFiles = CopyOnWriteArrayList(listOf(cbz(EXISTING, 100), cbz(OTHER, 200)))

    private val mangaDir = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns MANGA_TITLE
        every { listFiles() } answers { chapterFiles.toTypedArray() }
    }

    private val sourceDir = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns SOURCE_DIR_NAME
        every { listFiles() } returns arrayOf(mangaDir)
    }

    private val rootDir = mockk<UniFile>(relaxed = true) {
        every { listFiles() } returns arrayOf(sourceDir)
    }

    private val cache by lazy { newCache() }

    @AfterEach
    fun tearDown() {
        blockFirstLength?.countDown()
        AppGraphHolder.graphOrNull = null
        unmockkStatic(UniFile::class)
    }

    @Test
    fun `reading a size twice walks the manga folder once`() {
        awaitChapterCount(cache, 2)

        cache.getDownloadSize(manga) shouldBe 300
        cache.getDownloadSize(manga) shouldBe 300

        lengthReads.get() shouldBe 2
    }

    @Test
    fun `a chapter added after a size read is counted in the next read`() = runBlocking<Unit> {
        awaitChapterCount(cache, 2)
        cache.getDownloadSize(manga)

        chapterFiles += cbz(ADDED, 50)
        cache.addChapter(ADDED, mangaDir, manga)

        cache.getDownloadSize(manga) shouldBe 350
    }

    @Test
    fun `a chapter removed after a size read is dropped from the next read`() = runBlocking<Unit> {
        awaitChapterCount(cache, 2)
        cache.getDownloadSize(manga)

        chapterFiles.removeIf { it.name == "$EXISTING.cbz" }
        cache.removeChapter(chapter(EXISTING), manga)

        cache.getDownloadSize(manga) shouldBe 200
    }

    @Test
    fun `a size walk that overlaps a chapter add does not keep its result`() = runBlocking<Unit> {
        awaitChapterCount(cache, 2)
        val releaseWalk = CountDownLatch(1).also { blockFirstLength = it }

        val overlappingRead = async(Dispatchers.IO) { cache.getDownloadSize(manga) }
        walkStarted.await(10, TimeUnit.SECONDS) shouldBe true
        chapterFiles += cbz(ADDED, 50)
        cache.addChapter(ADDED, mangaDir, manga)
        releaseWalk.countDown()
        overlappingRead.await()

        cache.getDownloadSize(manga) shouldBe 350
    }

    @Test
    fun `a cache scan drops stored sizes`() {
        awaitChapterCount(cache, 2)
        cache.getDownloadSize(manga)

        chapterFiles += cbz(ADDED, 50)
        cache.invalidateCache()
        awaitChapterCount(cache, 3)

        cache.getDownloadSize(manga) shouldBe 350
    }

    @Test
    fun `a stored size is read from the index after a restart without a walk`() {
        restoreIndexFoldersAs(mangaDir)
        awaitChapterCount(cache, 2)
        cache.getDownloadSize(manga)
        Thread.sleep(INDEX_WRITE_WAIT_MS)
        val readsBeforeRestart = lengthReads.get()

        val restarted = newCache(downloadsDir = mockk(relaxed = true))
        awaitChapterCount(restarted, 2)

        restarted.getDownloadSize(manga) shouldBe 300
        lengthReads.get() shouldBe readsBeforeRestart
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `an index written before sizes were stored still loads`() {
        val oldIndex = OldRoot(
            sourceDirs = mapOf(
                SOURCE_ID to OldSource(mangaDirs = mapOf(MANGA_TITLE to OldManga(chapterDirs = setOf(EXISTING)))),
            ),
        )
        File(cacheDir, "dl_index_cache_v3").writeBytes(ProtoBuf.encodeToByteArray(oldIndex))

        awaitChapterCount(cache, 1)
    }

    private fun newCache(downloadsDir: UniFile = rootDir) = MangaDownloadCache(
        context = mockk<Context> { every { cacheDir } returns this@MangaDownloadCacheSizeTest.cacheDir },
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
            every { getDownloadsDirectory() } returns downloadsDir
            every { changes } returns MutableSharedFlow()
        },
    )

    private fun restoreIndexFoldersAs(dir: UniFile) {
        AppGraphHolder.graphOrNull = mockk<AppGraph> { every { application } returns mockk() }
        mockkStatic(UniFile::class)
        every { UniFile.fromUri(any(), any()) } returns dir
    }

    private fun awaitChapterCount(target: MangaDownloadCache, expected: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (target.getDownloadCount(manga) != expected) {
            check(System.nanoTime() < deadline) { "The cache never held $expected chapters" }
            Thread.sleep(10)
        }
    }

    private fun cbz(chapterName: String, size: Long) = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns false
        every { isFile } returns true
        every { name } returns "$chapterName.cbz"
        every { length() } answers {
            lengthReads.incrementAndGet()
            blockFirstLength?.let { latch ->
                if (firstLengthBlocked.compareAndSet(false, true)) {
                    walkStarted.countDown()
                    latch.await(10, TimeUnit.SECONDS)
                }
            }
            size
        }
    }

    private fun chapter(chapterName: String) = Chapter.create().copy(name = chapterName, scanlator = null)

    @Serializable
    private class OldRoot(val dir: String? = null, val sourceDirs: Map<Long, OldSource> = mapOf())

    @Serializable
    private class OldSource(val dir: String? = null, val mangaDirs: Map<String, OldManga> = mapOf())

    @Serializable
    private class OldManga(val dir: String? = null, val chapterDirs: Set<String> = setOf())

    private companion object {
        const val SOURCE_ID = 1L
        const val SOURCE_DIR_NAME = "Source"
        const val MANGA_TITLE = "Title"
        const val EXISTING = "Existing"
        const val OTHER = "Other"
        const val ADDED = "Added"
        const val INDEX_WRITE_WAIT_MS = 2_000L
    }
}
