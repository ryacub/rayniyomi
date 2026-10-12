package xyz.rayniyomi.plugin.lightnovel.data

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class NovelStorageRestorePreservationTest {
    @Test
    fun firstRestorePreservesIncomingTempNamedBook() {
        val dir = createTempDirectory("ln-startup-restore").toFile()
        try {
            val booksDir = File(dir, "light_novel_plugin/books").apply { mkdirs() }
            val incomingBookFile = File(booksDir, "550e8400-e29b-41d4-a716-446655440001.tmp.epub").apply {
                writeText("book contents")
            }
            val context = mockk<Context> {
                every { filesDir } returns dir
            }
            val library = NovelLibrary(listOf(NovelBook("book-1", "Book", incomingBookFile.name)))

            val restored = NovelStorage(context).restoreLibrary(library)

            assertTrue(restored)
            assertTrue(incomingBookFile.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun firstLibraryQueryPreservesTempSuffixedBookForLaterRestore() {
        val dir = createTempDirectory("ln-startup-late-restore").toFile()
        try {
            val booksDir = File(dir, "light_novel_plugin/books").apply { mkdirs() }
            val incomingBookFile = File(booksDir, "550e8400-e29b-41d4-a716-446655440004.tmp.epub").apply {
                writeText("book contents")
            }
            val context = mockk<Context> {
                every { filesDir } returns dir
            }
            val library = NovelLibrary(listOf(NovelBook("book-1", "Book", incomingBookFile.name)))
            val storage = NovelStorage(context)

            storage.listBooks()
            val restored = storage.restoreLibrary(library)

            assertTrue(restored)
            assertTrue(incomingBookFile.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun constructorDefersStorageInitializationUntilFirstLibraryQuery() {
        val dir = createTempDirectory("ln-startup-deferred").toFile()
        try {
            val rootDir = File(dir, "light_novel_plugin")
            val context = mockk<Context> {
                every { filesDir } returns dir
            }

            val storage = NovelStorage(context)

            assertFalse(rootDir.exists())
            storage.listBooks()
            assertTrue(rootDir.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun firstRestoreKeepsTempFileForLaterRestore() {
        val dir = createTempDirectory("ln-late-restore").toFile()
        try {
            val booksDir = File(dir, "light_novel_plugin/books").apply { mkdirs() }
            val laterBookFile = File(booksDir, "550e8400-e29b-41d4-a716-446655440000.tmp.epub").apply {
                writeText("book for a later restore")
            }
            val context = mockk<Context> {
                every { filesDir } returns dir
            }

            val storage = NovelStorage(context)
            storage.restoreLibrary(NovelLibrary())

            assertTrue(laterBookFile.exists())
            val laterLibrary = NovelLibrary(listOf(NovelBook("later-book", "Later book", laterBookFile.name)))
            assertTrue(storage.restoreLibrary(laterLibrary))

            assertTrue(laterBookFile.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun firstRestorePreservesTempNamedBookReferencedByLibrary() {
        val dir = createTempDirectory("ln-startup-referenced").toFile()
        try {
            val rootDir = File(dir, "light_novel_plugin")
            val booksDir = File(rootDir, "books").apply { mkdirs() }
            val referenced = File(booksDir, "550e8400-e29b-41d4-a716-446655440003.tmp.epub").apply {
                writeText("referenced book")
            }
            File(rootDir, "library.json").writeText(
                """{"schema_version":2,"library":{"books":[{"id":"book-1","title":"Book","epub_file_name":"550e8400-e29b-41d4-a716-446655440003.tmp.epub"}]}}""",
            )
            val context = mockk<Context> {
                every { filesDir } returns dir
            }

            NovelStorage(context).restoreLibrary(NovelLibrary())

            assertTrue(referenced.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
