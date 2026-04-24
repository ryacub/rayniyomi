package eu.kanade.tachiyomi.data.updater

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class AppUpdateInstallArtifactValidatorTest {

    @Test
    fun `isValidDownloadedFile returns true for non-trivial existing apk`() {
        val tempDir = createTempDirectory(prefix = "app-update-validator").toFile()
        val apkFile = File(tempDir, "update.apk").apply {
            writeBytes(ByteArray(64 * 1024))
        }

        val valid = AppUpdateInstallArtifactValidator.isValidDownloadedFile(apkFile, minBytes = 32 * 1024L)

        assertTrue(valid)
    }

    @Test
    fun `isValidDownloadedFile returns false for missing or stale artifact`() {
        val tempDir = createTempDirectory(prefix = "app-update-validator").toFile()
        val missing = File(tempDir, "missing.apk")
        val tiny = File(tempDir, "tiny.apk").apply {
            writeBytes(ByteArray(8 * 1024))
        }

        assertFalse(AppUpdateInstallArtifactValidator.isValidDownloadedFile(missing, minBytes = 32 * 1024L))
        assertFalse(AppUpdateInstallArtifactValidator.isValidDownloadedFile(tiny, minBytes = 32 * 1024L))
    }
}
