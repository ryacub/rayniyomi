package xyz.rayniyomi.plugin.lightnovel

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test
import xyz.rayniyomi.plugin.lightnovel.epub.EpubChapter
import xyz.rayniyomi.plugin.lightnovel.epub.EpubContent
import java.io.File
import kotlin.io.path.createTempDirectory

class ReaderBookLoaderTest {
    @Test
    fun loadsAndParsesBookOnIoDispatcher() = runBlocking {
        val callerThread = Thread.currentThread()
        var parserThread: Thread? = null
        val dir = createTempDirectory("ln-reader-loader").toFile()
        try {
            val root = File(dir, "light_novel_plugin").apply { mkdirs() }
            File(root, "books/reader.epub").apply {
                parentFile?.mkdirs()
                writeText("fixture")
            }
            File(root, "library.json").writeText(
                """{"schema_version":2,"library":{"books":[{"id":"reader-book","title":"Reader Book","epub_file_name":"reader.epub"}]}}""",
            )
            val context = mockk<Context> {
                every { filesDir } returns dir
            }

            val loaded = loadReaderBook(context, "reader-book") { file ->
                assertEquals("reader.epub", file.name)
                parserThread = Thread.currentThread()
                EpubContent("Parsed title", listOf(EpubChapter("Chapter", "Text")))
            }

            assertNotNull(loaded)
            assertEquals("Parsed title", loaded?.content?.title)
            assertNotSame(callerThread, parserThread)
        } finally {
            dir.deleteRecursively()
        }
    }
}
