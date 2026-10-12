package xyz.rayniyomi.plugin.lightnovel.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.util.stream.Stream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class NovelStorageMutationTest {
    private val tempDirs = mutableListOf<File>()

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
        tempDirs.forEach(File::deleteRecursively)
        tempDirs.clear()
    }

    @TestFactory
    fun mutationsPreserveMalformedAndFutureIndexes(): Stream<DynamicTest> {
        val mutationCases = listOf(
            "import" to { storage: NovelStorage, uri: Uri ->
                storage.importEpub(uri)
                Unit
            },
            "progress update" to { storage: NovelStorage, _: Uri -> storage.updateProgress("book-1", 1, 2) },
            "restore" to { storage: NovelStorage, _: Uri ->
                storage.restoreLibrary(NovelLibrary(listOf(testBook("restored"))))
            },
        )
        val indexCases = listOf(
            "malformed" to "{ this is not valid json }".encodeToByteArray(),
            "future schema" to """{"schema_version":999,"library":{"books":[]}}""".encodeToByteArray(),
        )
        return mutationCases.flatMap { (mutationName, mutation) ->
            indexCases.map { (indexName, originalBytes) ->
                DynamicTest.dynamicTest("$mutationName preserves the $indexName index") {
                    val dir = createTempDirectory("ln-mutation-test").toFile().also(tempDirs::add)
                    val epub = createTestEpub()
                    try {
                        val resolver = mockk<ContentResolver>()
                        every { resolver.openAssetFileDescriptor(any(), "r") } returns null
                        every { resolver.openInputStream(any()) } returns ByteArrayInputStream(epub.readBytes())
                        val context = mockk<Context> {
                            every { filesDir } returns dir
                            every { contentResolver } returns resolver
                        }
                        val storage = NovelStorage(context)
                        val libraryFile = File(dir, "light_novel_plugin/library.json")
                        libraryFile.parentFile?.mkdirs()
                        libraryFile.writeBytes(originalBytes)

                        val result = runCatching { mutation(storage, mockk<Uri>()) }

                        assertArrayEquals(originalBytes, libraryFile.readBytes())
                        if (mutationName == "restore") {
                            assertTrue(result.isSuccess)
                            assertFalse(result.getOrNull() as Boolean)
                        }
                        if (mutationName == "import") {
                            val booksDir = File(dir, "light_novel_plugin/books")
                            assertTrue(booksDir.listFiles().orEmpty().isEmpty())
                        }
                    } finally {
                        epub.delete()
                    }
                }
            }
        }.stream()
    }

    private fun createTestEpub(): File {
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

    private fun testBook(id: String) = NovelBook(
        id = id,
        title = "Novel",
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
