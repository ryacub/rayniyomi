package xyz.rayniyomi.lightnovel.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LightNovelBackupContractTest {
    @Test
    fun `defines the shared backup protocol`() {
        assertEquals("xyz.rayniyomi.plugin.lightnovel.backup", LightNovelBackupContract.AUTHORITY)
        assertEquals("library", LightNovelBackupContract.PATH_LIBRARY)
        assertEquals(
            "content://xyz.rayniyomi.plugin.lightnovel.backup/library",
            LightNovelBackupContract.CONTENT_URI,
        )
        assertEquals("restore_backup", LightNovelBackupContract.METHOD_RESTORE_BACKUP)
        assertEquals("backup_data", LightNovelBackupContract.EXTRA_BACKUP_DATA)
        assertEquals("success", LightNovelBackupContract.RESULT_SUCCESS)
        assertEquals(
            listOf("id", "title", "epub_file_name", "last_read_chapter", "last_read_offset", "updated_at"),
            LightNovelBackupContract.LIBRARY_COLUMNS,
        )
    }
}
