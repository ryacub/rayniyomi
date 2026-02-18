package xyz.rayniyomi.plugin.lightnovel.backup

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import xyz.rayniyomi.plugin.lightnovel.data.NovelLibrary

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
