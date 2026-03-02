package eu.kanade.tachiyomi.data.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path

class ErrorLogGuardsTest {

    @TempDir
    lateinit var tempDir: Path

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
    fun `writeErrorLogOutcome returns NoErrors when no errors exist`() {
        val outcome = writeErrorLogOutcome(hasErrors = false) {
            tempDir.resolve("errors.txt").toFile()
        }

        assertTrue(outcome is ErrorLogWriteOutcome.NoErrors)
    }

    @Test
    fun `writeErrorLogOutcome returns Created when file write succeeds`() {
        val expected = tempDir.resolve("errors.txt").toFile().apply { writeText("error") }
        val outcome = writeErrorLogOutcome(hasErrors = true) { expected }

        assertTrue(outcome is ErrorLogWriteOutcome.Created)
        assertEquals(expected, (outcome as ErrorLogWriteOutcome.Created).file)
    }

    @Test
    fun `writeErrorLogOutcome returns Failed when file write throws`() {
        val outcome = writeErrorLogOutcome(hasErrors = true) {
            throw IOException("boom")
        }

        assertTrue(outcome is ErrorLogWriteOutcome.Failed)
        assertEquals("boom", (outcome as ErrorLogWriteOutcome.Failed).cause?.message)
    }

    @Test
    fun `resolveRestoreErrorLogFileForAction returns null when no errors`() {
        val created = ErrorLogWriteOutcome.Created(tempDir.resolve("errors.txt").toFile().apply { writeText("error") })

        assertNull(resolveRestoreErrorLogFileForAction(errorCount = 0, errorLogWriteOutcome = created))
    }

    @Test
    fun `resolveRestoreErrorLogFileForAction returns null when outcome is NoErrors`() {
        assertNull(
            resolveRestoreErrorLogFileForAction(
                errorCount = 1,
                errorLogWriteOutcome = ErrorLogWriteOutcome.NoErrors,
            ),
        )
    }

    @Test
    fun `resolveRestoreErrorLogFileForAction returns null when outcome is Failed`() {
        assertNull(
            resolveRestoreErrorLogFileForAction(
                errorCount = 1,
                errorLogWriteOutcome = ErrorLogWriteOutcome.Failed(IOException("write failed")),
            ),
        )
    }

    @Test
    fun `resolveRestoreErrorLogFileForAction returns null when created file does not exist`() {
        val missingFile = tempDir.resolve("missing.txt").toFile()

        assertNull(
            resolveRestoreErrorLogFileForAction(
                errorCount = 1,
                errorLogWriteOutcome = ErrorLogWriteOutcome.Created(missingFile),
            ),
        )
    }

    @Test
    fun `resolveRestoreErrorLogFileForAction returns file when errors exist and file exists`() {
        val existingFile = tempDir.resolve("errors.txt").toFile().apply {
            writeText("error")
        }
        val resolved = resolveRestoreErrorLogFileForAction(
            errorCount = 1,
            errorLogWriteOutcome = ErrorLogWriteOutcome.Created(existingFile),
        )

        assertNotNull(resolved)
        assertEquals(existingFile, resolved)
    }

    @Test
    fun `shouldAttachRestoreErrorLogAction returns true only for action-eligible outcome`() {
        val existingFile = tempDir.resolve("errors.txt").toFile().apply { writeText("error") }
        val created = ErrorLogWriteOutcome.Created(existingFile)
        val noErrors = ErrorLogWriteOutcome.NoErrors
        val failed = ErrorLogWriteOutcome.Failed(IOException("write failed"))

        assertTrue(shouldAttachRestoreErrorLogAction(1, created))
        assertFalse(shouldAttachRestoreErrorLogAction(0, created))
        assertFalse(shouldAttachRestoreErrorLogAction(1, noErrors))
        assertFalse(shouldAttachRestoreErrorLogAction(1, failed))
    }
}
