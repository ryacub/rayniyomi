package xyz.rayniyomi.plugin.lightnovel.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xyz.rayniyomi.plugin.lightnovel.backup.BackupLightNovel
import xyz.rayniyomi.plugin.lightnovel.backup.BackupLightNovelRestorer
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class NovelStorageConcurrencyTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private var tempDir: File? = null

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        tempDir?.deleteRecursively()
        tempDir = null
    }

    @Test
    fun `restore and progress use the same process wide lock`() {
        val context = contextWithTempFilesDir()
        val storage = NovelStorage(context)
        val book = testBook()
        val orphan = testBook(id = "orphan", title = "Orphan")
        assertTrue(storage.restoreLibrary(NovelLibrary(listOf(book, orphan))))
        File(File(tempDir!!, "light_novel_plugin"), "books/${book.epubFileName}").writeText("book")

        val restore = BackupLightNovelRestorer(context)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val ready = CountDownLatch(2)
            val restoreFinished = CountDownLatch(1)
            val progressFinished = CountDownLatch(1)
            executor.submit {
                try {
                    ready.countDown()
                    start.await()
                    restore.restoreBackup(backupFor(testBook(id = "book-2", title = "Restored")))
                } finally {
                    restoreFinished.countDown()
                }
            }
            executor.submit {
                try {
                    ready.countDown()
                    start.await()
                    NovelStorage(context).updateProgress(book.id, chapterIndex = 2, charOffset = 20)
                } finally {
                    progressFinished.countDown()
                }
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(restoreFinished.await(2, TimeUnit.SECONDS))
            assertTrue(progressFinished.await(2, TimeUnit.SECONDS))
            assertValidLibrary(storage)
            val books = storage.listBooks()
            assertEquals(setOf("book-1", "book-2"), books.map { it.id }.toSet())
            assertTrue(books.none { it.id == orphan.id })
            assertTrue(books.any { it.id == book.id && it.lastReadChapter == 2 && it.lastReadOffset == 20 })
            assertTrue(books.any { it.id == "book-2" && it.title == "Restored" })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `restore and import use the same process wide lock`() {
        val epub = testEpub()
        val context = contextWithTempFilesDir(epub)
        val storage = NovelStorage(context)
        val book = testBook()
        assertTrue(storage.restoreLibrary(NovelLibrary(listOf(book))))
        File(File(tempDir!!, "light_novel_plugin"), "books/${book.epubFileName}").writeBytes(epub.readBytes())

        val restore = BackupLightNovelRestorer(context)
        val importUri = mockk<Uri>()
        val executor = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val ready = CountDownLatch(2)
            val restoreFinished = CountDownLatch(1)
            val importFinished = CountDownLatch(1)
            val restoreFuture = executor.submit {
                try {
                    ready.countDown()
                    start.await()
                    restore.restoreBackup(backupFor(book.copy(title = "Restored")))
                } finally {
                    restoreFinished.countDown()
                }
            }
            val importFuture = executor.submit {
                try {
                    ready.countDown()
                    start.await()
                    NovelStorage(context).importEpub(importUri)
                } finally {
                    importFinished.countDown()
                }
            }

            assertTrue(ready.await(2, TimeUnit.SECONDS))
            start.countDown()
            assertTrue(restoreFinished.await(2, TimeUnit.SECONDS))
            assertTrue(importFinished.await(2, TimeUnit.SECONDS))
            restoreFuture.get(2, TimeUnit.SECONDS)
            importFuture.get(2, TimeUnit.SECONDS)
            assertValidLibrary(storage)
            val books = storage.listBooks()
            val booksDir = File(tempDir!!, "light_novel_plugin/books")
            val trackedFiles = books.map { it.epubFileName }.toSet()
            val storedFiles = booksDir.listFiles().orEmpty().filter { it.extension == "epub" }.map { it.name }.toSet()
            assertEquals(2, books.size)
            assertTrue(books.all { File(booksDir, it.epubFileName).exists() })
            assertTrue(storedFiles == trackedFiles)
        } finally {
            executor.shutdownNow()
            epub.delete()
        }
    }

    private fun assertValidLibrary(storage: NovelStorage) {
        assertTrue(storage.checkIntegrity() is NovelStorageState.Ok)
        assertTrue(storage.listBooks().all { it.id.isNotBlank() && it.title.isNotBlank() })
    }

    private fun backupFor(book: NovelBook): ByteArray {
        return json.encodeToString(BackupLightNovel(library = NovelLibrary(listOf(book)))).encodeToByteArray()
    }

    private fun contextWithTempFilesDir(epub: File? = null): Context {
        val dir = createTempDirectory("ln-concurrency-test").toFile()
        tempDir = dir
        val resolver = mockk<ContentResolver>()
        if (epub != null) {
            every { resolver.openAssetFileDescriptor(any(), "r") } returns null
            every { resolver.openInputStream(any()) } answers { ByteArrayInputStream(epub.readBytes()) }
        }
        return mockk {
            every { filesDir } returns dir
            every { contentResolver } returns resolver
        }
    }

    private fun testEpub(): File {
        return File.createTempFile("novel", ".epub").also { epub ->
            ZipOutputStream(epub.outputStream()).use { zip ->
                zip.putText("OEBPS/content.opf", OPF_XML)
                zip.putText("OEBPS/chapter.xhtml", CHAPTER_XML)
            }
        }
    }

    private fun ZipOutputStream.putText(path: String, text: String) {
        putNextEntry(ZipEntry(path))
        write(text.toByteArray())
        closeEntry()
    }

    private fun testBook(id: String = "book-1", title: String = "Original") = NovelBook(
        id = id,
        title = title,
        epubFileName = "$id.epub",
        updatedAt = 1L,
    )

    private companion object {
        const val OPF_XML = """
            <package xmlns:dc="http://purl.org/dc/elements/1.1/">
              <metadata><dc:title>Imported Novel</dc:title></metadata>
              <manifest><item id="chapter" href="chapter.xhtml" /></manifest>
              <spine><itemref idref="chapter" /></spine>
            </package>
        """
        const val CHAPTER_XML = "<html><body><p>Imported text</p></body></html>"
    }
}
