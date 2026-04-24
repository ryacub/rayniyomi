package eu.kanade.tachiyomi

import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.tachiyomi.util.CrashLogUtil
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class R635ExportFilenamesTest {

    @Test
    fun `library export filename uses rayniyomi branding`() {
        assertEquals("rayniyomi_library.csv", SettingsDataScreen.LIBRARY_EXPORT_FILENAME)
    }

    @Test
    fun `crash log filename uses rayniyomi branding`() {
        assertEquals("rayniyomi_crash_logs.txt", CrashLogUtil.CRASH_LOG_FILENAME)
    }
}
