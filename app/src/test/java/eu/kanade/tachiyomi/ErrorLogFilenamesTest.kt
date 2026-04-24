package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ErrorLogFilenamesTest {

    @Test
    fun `manga update error log filename uses rayniyomi branding`() {
        assertEquals("rayniyomi_update_errors.txt", MangaLibraryUpdateJob.ERROR_LOG_FILENAME)
    }

    @Test
    fun `anime update error log filename uses rayniyomi branding`() {
        assertEquals("rayniyomi_update_errors.txt", AnimeLibraryUpdateJob.ERROR_LOG_FILENAME)
    }

    @Test
    fun `restore error log filename uses rayniyomi branding`() {
        assertEquals("rayniyomi_restore_error.txt", BackupRestorer.RESTORE_ERROR_LOG_FILENAME)
    }
}
