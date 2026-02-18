package xyz.rayniyomi.plugin.lightnovel.backup

import android.content.Context
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BackupLightNovelRestorerTest {
    private lateinit var context: Context
    private lateinit var restorer: BackupLightNovelRestorer

    @TempDir
    private lateinit var tempDir: File

    @BeforeEach
    fun setup() {
        tempDir = File(tempDir.toFile(), "test_plugin_data")
        tempDir.mkdirs()

        val rootDir = File(tempDir, "light_novel_plugin")
        rootDir.mkdirs()
        val booksDir = File(rootDir, "books")
        booksDir.mkdirs()

        context = MockContext(rootDir.absolutePath)
        restorer = BackupLightNovelRestorer(context)
    }

    @Test
    fun `returns false for corrupt backup`() {
        val restorer = BackupLightNovelRestorer(context)
        val corruptData =
            byteArrayOf(0x7B, 0x22, 0x69, 0x6E, 0x76, 0x61, 0x6C, 0x69, 0x64, 0x20, 0x64, 0x61, 0x74, 0x61)

        val result = runCatching {
            restorer.restoreBackup(corruptData)
        }.getOrElse { false }

        assertFalse(result)
    }

    @Test
    fun `returns true for version mismatch (graceful degradation)`() {
        val library = xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary(
            books = listOf(
                xyz.rayniyomi.plugin.lightnovel.data.NovelBook(
                    id = "1",
                    title = "Test",
                    epubFileName = "1.epub",
                    lastReadChapter = 0,
                    lastReadOffset = 0,
                    updatedAt = System.currentTimeMillis(),
                ),
            ),
        )
        val oldVersionBackup = BackupLightNovel(
            version = BackupLightNovel.MIN_COMPATIBLE_VERSION - 1,
            library = library,
            timestamp = System.currentTimeMillis(),
        )
        val backupData = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }.encodeToString(oldVersionBackup).encodeToByteArray()

        val existingLibrary = xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary(
            books = listOf(
                xyz.rayniyomi.plugin.lightnovel.data.NovelBook(
                    id = "999",
                    title = "Existing",
                    epubFileName = "999.epub",
                    lastReadChapter = 0,
                    lastReadOffset = 0,
                    updatedAt = System.currentTimeMillis(),
                ),
            ),
        )

        val storage = xyz.rayniyomi.plugin.lightnovel.data.NovelStorage(context)
        storage.writeLibrary(existingLibrary)

        val result = runCatching {
            restorer.restoreBackup(backupData)
        }.getOrElse { false }

        assertTrue(result)

        val restored = storage.readLibrary()
        assertEquals(existingLibrary, restored)
    }

    @Test
    fun `atomic restore prevents data loss on failure`() {
        val existingLibrary = xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary(
            books = listOf(
                xyz.rayniyomi.plugin.lightnovel.data.NovelBook(
                    id = "1",
                    title = "Existing Novel 1",
                    epubFileName = "1.epub",
                    lastReadChapter = 5,
                    lastReadOffset = 500,
                    updatedAt = System.currentTimeMillis(),
                ),
                xyz.rayniyomi.plugin.lightnovel.data.NovelBook(
                    id = "2",
                    title = "Existing Novel 2",
                    epubFileName = "2.epub",
                    lastReadChapter = 10,
                    lastReadOffset = 750,
                    updatedAt = System.currentTimeMillis(),
                ),
            ),
        )

        val storage = xyz.rayniyomi.plugin.lightnovel.data.NovelStorage(context)
        storage.writeLibrary(existingLibrary)

        val invalidBackupData = byteArrayOf(0x7B, 0x22, 0x00, 0x00)

        val result = runCatching {
            restorer.restoreBackup(invalidBackupData)
        }.getOrElse { false }

        assertFalse(result)

        val restored = storage.readLibrary()
        assertEquals(2, restored.books.size)
        assertEquals("Existing Novel 1", restored.books[0].title)
        assertEquals("Existing Novel 2", restored.books[1].title)
    }
}
