package xyz.rayniyomi.plugin.lightnovel.data

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class NovelStorageStartupCleanupTest {
    @Test
    fun firstRestorePreservesIncomingTempNamedBook() {
        val dir = createTempDirectory("ln-startup-restore").toFile()
        try {
            val booksDir = File(dir, "light_novel_plugin/books").apply { mkdirs() }
            val incomingBookFile = File(booksDir, "chapter.tmp.epub").apply { writeText("book contents") }
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
    fun constructorDefersStaleCleanupUntilFirstLibraryQuery() {
        val dir = createTempDirectory("ln-startup-deferred").toFile()
        try {
            val booksDir = File(dir, "light_novel_plugin/books").apply { mkdirs() }
            val stale = File(booksDir, "stale.tmp.epub").apply { writeText("interrupted import") }
            val context = mockk<Context> {
                every { filesDir } returns dir
            }

            val storage = NovelStorage(context)

            assertTrue(stale.exists())
            storage.listBooks()
            assertFalse(stale.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun constructorRemovesStaleTempFilesOnlyOnFirstInitialization() {
        val dir = createTempDirectory("ln-startup-cleanup").toFile()
        try {
            val booksDir = File(dir, "light_novel_plugin/books").apply { mkdirs() }
            val stale = File(booksDir, "stale.tmp.epub").apply { writeText("interrupted import") }
            val context = mockk<Context> {
                every { filesDir } returns dir
            }

            NovelStorage(context)

            assertFalse(stale.exists())

            val activeImport = File(booksDir, "active.tmp.epub").apply { writeText("active import") }
            NovelStorage(context)

            assertTrue(activeImport.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun constructorPreservesTempNamedBookReferencedByLibrary() {
        val dir = createTempDirectory("ln-startup-referenced").toFile()
        try {
            val rootDir = File(dir, "light_novel_plugin")
            val booksDir = File(rootDir, "books").apply { mkdirs() }
            val referenced = File(booksDir, "chapter.tmp.epub").apply { writeText("referenced book") }
            File(rootDir, "library.json").writeText(
                """{"schema_version":2,"library":{"books":[{"id":"book-1","title":"Book","epub_file_name":"chapter.tmp.epub"}]}}""",
            )
            val context = mockk<Context> {
                every { filesDir } returns dir
            }

            NovelStorage(context)

            assertTrue(referenced.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
