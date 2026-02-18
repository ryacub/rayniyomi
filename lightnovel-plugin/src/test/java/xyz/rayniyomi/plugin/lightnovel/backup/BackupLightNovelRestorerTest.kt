package xyz.rayniyomi.plugin.lightnovel.backup

import android.content.ContentValues
import android.content.ContentResolver
import android.net.Uri
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xyz.rayniyomi.plugin.lightnovel.backup.LightNovelBackupContentProvider
import xyz.rayniyomi.plugin.lightnovel.backup.LightNovelBackupContentProvider.Companion.CONTENT_URI
import xyz.rayniyomi.plugin.lightnovel.backup.LightNovelBackupContentProvider.Companion.COLUMNS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook

class BackupLightNovelRestorerTest {
    private lateinit var context: Context
    private lateinit var restorer: BackupLightNovelRestorer
    private lateinit var contentResolver: ContentResolver

    @BeforeEach
    fun setup() {
        context = MockContext()
        restorer = BackupLightNovelRestorer(context)
        contentResolver = context.contentResolver
    }

    @Test
    fun `returns false for corrupt backup`() {
        val corruptData = byteArrayOf(0x7B, 0x22, 0x69, 0x6E, 0x76, 0x61, 0x6C, 0x69, 0x64, 0x20, 0x64, 0x61, 0x74, 0x61)

        val result = runCatching {
            restorer.restoreBackup(corruptData)
        }.getOrElse { false }

        assertFalse(result)
    }

    @Test
    fun `returns true for version mismatch (graceful degradation)`() {
        val library = NovelLibrary(
            books = listOf(
                NovelBook(
                    id = "1",
                    title = "Test",
                    epubFileName = "1.epub",
                    lastReadChapter = 0,
                    lastReadOffset = 0,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
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

        runTest {
            // Insert existing library via ContentProvider
            contentResolver.insert(
                LightNovelBackupContentProvider.CONTENT_URI,
                ContentValues().apply {
                    put(COLUMNS[0], "999")
                    put(COLUMNS[1], "Existing")
                    put(COLUMNS[2], "999.epub")
                    put(COLUMNS[3], 0)
                    put(COLUMNS[4], 0)
                    put(COLUMNS[5], System.currentTimeMillis())
                },
            )

            // Attempt to restore with version mismatch backup
            val result = withContext(Dispatchers.IO) {
                restorer.restoreBackup(backupData)
            }

            // Result should be true (graceful degradation, library unchanged)
            assertTrue(result)

            // Query ContentProvider to verify library was not restored
            val cursor = contentResolver.query(
                LightNovelBackupContentProvider.CONTENT_URI,
                COLUMNS,
                null,
                null,
                null,
            )
            cursor.use {
                // Should have the existing library (from setup) not the restore
                assertEquals(1, it.count)
                it.moveToFirst()
                assertEquals("999", it.getString(0))
            }
        }
    }

    @Test
    fun `atomic restore prevents data loss on failure`() {
        runTest {
            // Insert existing library via ContentProvider
            contentResolver.insert(
                LightNovelBackupContentProvider.CONTENT_URI,
                ContentValues().apply {
                    put(COLUMNS[0], "1")
                    put(COLUMNS[1], "Existing Novel 1")
                    put(COLUMNS[2], "1.epub")
                    put(COLUMNS[3], 5)
                    put(C4], 500)
                    put(COLUMNS[5], System.currentTimeMillis())
                },
            )

            val invalidBackupData = byteArrayOf(0x7B, 0x22, 0x00, 0x00)

            val result = withContext(Dispatchers.IO) {
                restorer.restoreBackup(invalidBackupData)
            }.getOrElse { false }

            assertFalse(result)

            // Query ContentProvider to verify existing library is still intact
            val cursor = contentResolver.query(
                LightNovelBackupContentProvider.CONTENT_URI,
                COLUMNS,
                null,
                null,
                null,
            )
            cursor.use {
                assertEquals(1, it.count)
                it.moveToFirst()
                assertEquals("Existing Novel 1", it.getString(0))
            }
        }
    }
}
