package xyz.rayniyomi.plugin.lightnovel.backup

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class BackupLightNovelRestorerTest {
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
    fun `restoreBackup returns false for malformed backup data`() {
        val restorer = BackupLightNovelRestorer(contextWithTempFilesDir())

        val result = restorer.restoreBackup("not-json".encodeToByteArray())

        assertFalse(result)
    }

    @Test
    fun `restoreBackup returns false for invalid book payload`() {
        val restorer = BackupLightNovelRestorer(contextWithTempFilesDir())
        val invalidPayload = """
            {
              "version": 1,
              "timestamp": 123,
              "library": {
                "books": [{
                  "id": "",
                  "title": "Test",
                  "epub_file_name": "test.epub",
                  "last_read_chapter": 0,
                  "last_read_offset": 0,
                  "updated_at": 1
                }]
              }
            }
        """.trimIndent().encodeToByteArray()

        val result = restorer.restoreBackup(invalidPayload)

        assertFalse(result)
    }

    @Test
    fun `restoreBackup returns false for unsupported backup version`() {
        val restorer = BackupLightNovelRestorer(contextWithTempFilesDir())
        val unsupportedVersionPayload = """
            {
              "version": 99,
              "timestamp": 123,
              "library": { "books": [] }
            }
        """.trimIndent().encodeToByteArray()

        val result = restorer.restoreBackup(unsupportedVersionPayload)

        assertFalse(result)
    }

    private fun contextWithTempFilesDir(): Context {
        val dir = createTempDirectory("ln-restore-test").toFile()
        tempDir = dir
        return mockk(relaxed = true) {
            every { filesDir } returns dir
        }
    }
}
