package eu.kanade.tachiyomi.data.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class ErrorLogGuardsTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `asShareableErrorLogFile returns null for NoErrors`() {
        assertNull(asShareableErrorLogFile(ErrorLogFileResult.NoErrors))
    }

    @Test
    fun `asShareableErrorLogFile returns null for missing Created file`() {
        val missingFile = tempDir.resolve("missing.txt").toFile()

        assertNull(asShareableErrorLogFile(ErrorLogFileResult.Created(missingFile)))
    }

    @Test
    fun `asShareableErrorLogFile returns file for existing Created file`() {
        val existingFile = tempDir.resolve("errors.txt").toFile().apply {
            writeText("error")
        }

        assertTrue(asShareableErrorLogFile(ErrorLogFileResult.Created(existingFile)) == existingFile)
    }

    @Test
    fun `asShareableErrorLogFile returns null for Failed result`() {
        val failure = RuntimeException("write failed")

        assertNull(asShareableErrorLogFile(ErrorLogFileResult.Failed(failure)))
    }

    @Test
    fun `shouldAttachRestoreErrorLogAction returns false when no errors`() {
        val existingFile = tempDir.resolve("errors.txt").toFile().apply {
            writeText("error")
        }

        assertFalse(shouldAttachRestoreErrorLogAction(0, ErrorLogFileResult.Created(existingFile)))
    }

    @Test
    fun `shouldAttachRestoreErrorLogAction returns false when file missing on Created result`() {
        val missingFile = tempDir.resolve("missing.txt").toFile()

        assertFalse(shouldAttachRestoreErrorLogAction(1, ErrorLogFileResult.Created(missingFile)))
    }

    @Test
    fun `shouldAttachRestoreErrorLogAction returns false for Failed result`() {
        assertFalse(shouldAttachRestoreErrorLogAction(1, ErrorLogFileResult.Failed(RuntimeException("boom"))))
    }

    @Test
    fun `shouldAttachRestoreErrorLogAction returns true when errors exist and Created file exists`() {
        val existingFile = tempDir.resolve("errors.txt").toFile().apply {
            writeText("error")
        }

        assertTrue(shouldAttachRestoreErrorLogAction(1, ErrorLogFileResult.Created(existingFile)))
    }
}
