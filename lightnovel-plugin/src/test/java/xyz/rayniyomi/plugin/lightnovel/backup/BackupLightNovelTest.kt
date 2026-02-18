package xyz.rayniyomi.plugin.lightnovel.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary
import xyz.rayniyomi.plugin.lightnovel.backup.LightNovelBackupContentProvider
import xyz.rayniyomi.plugin.lightnovel.backup.LightNovelBackupContentProvider.Companion.CONTENT_URI
import xyz.rayniyomi.plugin.lightnovel.backup.LightNovelBackupContentProvider.Companion.COLUMNS
import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals

class BackupLightNovelTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun `serialization round-trip preserves data`() {
        val library = NovelLibrary(
            books = listOf(
                NovelBook(
                    id = "123456",
                    title = "Test Novel",
                    epubFileName = "123456.epub",
                    lastReadChapter = 5,
                    lastReadOffset = 1234,
                    updatedAt = 1234567890L,
                ),
            )
        )
        val backup = BackupLightNovel(library = library)
        val serialized = json.encodeToString(backup)
        val deserialized = json.decodeFromString<BackupLightNovel>(serialized)

        assertEquals(library, deserialized.library)
        assertEquals(BackupLightNovel.BACKUP_VERSION, deserialized.version)
        assertTrue(deserialized.timestamp > 0)
    }

    @Test
    fun `version field defaults correctly`() {
        val backup = BackupLightNovel(library = NovelLibrary())
        assertEquals(BackupLightNovel.BACKUP_VERSION, backup.version)
    }

    @Test
    fun `empty library serializes correctly`() {
        val backup = BackupLightNovel(library = NovelLibrary())
        val serialized = json.encodeToString(backup)
        assertTrue(serialized.contains("\"version\":1"))
        assertTrue(serialized.contains("\"books\":[]"))
    }

    @Test
    fun `content provider exposes library data correctly`() {
        val backup = BackupLightNovel(
            library = NovelLibrary(
                books = listOf(
                    NovelBook(
                        id = "1",
                        title = "Test Book",
                        epubFileName = "1.epub",
                        lastReadChapter = 0,
                        lastReadOffset = 0,
                        updatedAt = 1000000L,
                    ),
                ),
            )
        )
        val serialized = json.encodeToString(backup)
        
        runTest {
            // Write backup to context
            context.contentResolver.insert(
                LightNovelBackupContentProvider.CONTENT_URI,
                ContentValues().apply {
                    put(LightNovelBackupContentProvider.Companion.COLUMNS[0], "1")
                    put(LightNovelBackupContentProvider.Companion.COLUMNS[1], "Test Book")
                    put(LightNovelBackupContentProvider.Companion.COLUMNS[2], "1.epub")
                    put(LightNovelBackupContentProvider.Companion.COLUMNS[3], 0)
                    put(LightNovelBackupContentProvider.Companion.COLUMNS[4], 0)
                    put(LightNovelBackupContentProvider.Companion.COLUMNS[5], 1000000L)
                },
            )
        }
    }
}

    @Test
    fun `serialization round-trip preserves data`() {
        val library = NovelLibrary(
            books = listOf(
                NovelBook(
                    id = "123456",
                    title = "Test Novel",
                    epubFileName = "123456.epub",
                    lastReadChapter = 5,
                    lastReadOffset = 1234,
                    updatedAt = 1234567890L,
                ),
            ),
        )
        val backup = BackupLightNovel(library = library)
        val serialized = json.encodeToString(backup)
        val deserialized = json.decodeFromString<BackupLightNovel>(serialized)

        assertEquals(library, deserialized.library)
        assertEquals(BackupLightNovel.BACKUP_VERSION, deserialized.version)
        assertTrue(deserialized.timestamp > 0)
    }

    @Test
    fun `version field defaults correctly`() {
        val backup = BackupLightNovel(library = NovelLibrary())
        assertEquals(BackupLightNovel.BACKUP_VERSION, backup.version)
    }

    @Test
    fun `empty library serializes correctly`() {
        val backup = BackupLightNovel(library = NovelLibrary())
        val serialized = json.encodeToString(backup)
        assertTrue(serialized.contains("\"version\":1"))
        assertTrue(serialized.contains("\"books\":[]"))
    }
}
