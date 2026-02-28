package eu.kanade.tachiyomi.data.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ErrorLogGuardsTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `hasShareableErrorLogFile returns false for null file`() {
        assertFalse(hasShareableErrorLogFile(null))
    }

    @Test
    fun `hasShareableErrorLogFile returns false for missing file`() {
        val missingFile = tempDir.resolve("missing.txt").toFile()

        assertFalse(hasShareableErrorLogFile(missingFile))
    }

    @Test
    fun `hasShareableErrorLogFile returns true for existing file`() {
        val existingFile = tempDir.resolve("errors.txt").toFile().apply {
            writeText("error")
        }

        assertTrue(hasShareableErrorLogFile(existingFile))
    }

    @Test
    fun `shouldAttachRestoreErrorLogAction returns false when no errors`() {
        val existingFile = tempDir.resolve("errors.txt").toFile().apply {
            writeText("error")
        }

        assertFalse(shouldAttachRestoreErrorLogAction(0, existingFile))
    }

    @Test
    fun `shouldAttachRestoreErrorLogAction returns false when file missing`() {
        val missingFile = tempDir.resolve("missing.txt").toFile()

        assertFalse(shouldAttachRestoreErrorLogAction(1, missingFile))
    }

    @Test
    fun `shouldAttachRestoreErrorLogAction returns true when errors exist and file exists`() {
        val existingFile = tempDir.resolve("errors.txt").toFile().apply {
            writeText("error")
        }

        assertTrue(shouldAttachRestoreErrorLogAction(1, existingFile))
    }
}
