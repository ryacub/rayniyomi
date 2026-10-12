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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.io.InputStream
import kotlin.io.path.createTempDirectory

class NovelStorageImportLimitTest {
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
    fun importRejectsStreamedBytesAboveLimitWhenProviderLengthIsUnknown() {
        val dir = createTempDirectory("ln-import-limit").toFile()
        tempDir = dir
        val resolver = mockk<ContentResolver>()
        every { resolver.openAssetFileDescriptor(any(), "r") } returns null
        every { resolver.openInputStream(any()) } returns RepeatedByteInputStream(MAX_IMPORT_SIZE_BYTES + 1L)
        val context = mockk<Context> {
            every { filesDir } returns dir
            every { contentResolver } returns resolver
        }
        val storage = NovelStorage(context)
        val originalLibrary = """{"schema_version":2,"library":{"books":[]}}""".encodeToByteArray()
        val libraryFile = File(dir, "light_novel_plugin/library.json").apply { writeBytes(originalLibrary) }

        val error = assertThrows(ImportTooLargeException::class.java) {
            storage.importEpub(mockk<Uri>())
        }

        assertTrue(error.message.orEmpty().contains("limit=$MAX_IMPORT_SIZE_BYTES"))
        assertArrayEquals(originalLibrary, libraryFile.readBytes())
        assertTrue(File(dir, "light_novel_plugin/books").listFiles().orEmpty().isEmpty())
    }

    private class RepeatedByteInputStream(totalBytes: Long) : InputStream() {
        private var remaining = totalBytes

        override fun read(): Int {
            if (remaining == 0L) return -1
            remaining -= 1
            return 0
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0L) return -1
            val count = minOf(length.toLong(), remaining).toInt()
            buffer.fill(0, offset, offset + count)
            remaining -= count
            return count
        }
    }

    private companion object {
        const val MAX_IMPORT_SIZE_BYTES = 100L * 1024L * 1024L
    }
}
