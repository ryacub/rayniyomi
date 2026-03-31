package eu.kanade.tachiyomi.lint

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

/**
 * Tests for ComposeMigrationGuardrail.
 *
 * The guardrail detects re-introduction of XML layout files for screens that have
 * been migrated to Compose. It scans the layout directory for any file whose name
 * (without extension) exactly matches an entry in the migratedLayouts set.
 */
class ComposeMigrationGuardrailTest {

    @TempDir
    lateinit var tempDir: Path

    // ============================================================================
    // NON-EXISTENT LAYOUT DIR
    // ============================================================================

    @Test
    fun `findReintroductions returns empty list when layout dir does not exist`() {
        val layoutDir = tempDir.resolve("nonexistent/layout").toFile()
        val guardrail = ComposeMigrationGuardrail(layoutDir, setOf("reader_error"))

        val result = guardrail.findReintroductions()

        assertEquals(emptyList<File>(), result)
    }

    // ============================================================================
    // EMPTY LAYOUT DIR
    // ============================================================================

    @Test
    fun `findReintroductions returns empty list when layout dir has no files`() {
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        val guardrail = ComposeMigrationGuardrail(layoutDir, setOf("reader_error"))

        val result = guardrail.findReintroductions()

        assertEquals(emptyList<File>(), result)
    }

    // ============================================================================
    // NO VIOLATIONS
    // ============================================================================

    @Test
    fun `findReintroductions returns empty list when no layout matches migrated names`() {
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        layoutDir.resolve("unrelated_screen.xml").writeText("<layout/>")
        layoutDir.resolve("pref_widget_switch_material.xml").writeText("<layout/>")
        val guardrail = ComposeMigrationGuardrail(
            layoutDir,
            setOf("reader_error", "download_header", "player_layout"),
        )

        val result = guardrail.findReintroductions()

        assertEquals(emptyList<File>(), result)
    }

    // ============================================================================
    // SINGLE VIOLATION
    // ============================================================================

    @Test
    fun `findReintroductions returns file when migrated layout is reintroduced`() {
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        val reintroduced = layoutDir.resolve("reader_error.xml").apply { writeText("<layout/>") }
        val guardrail = ComposeMigrationGuardrail(layoutDir, setOf("reader_error"))

        val result = guardrail.findReintroductions()

        assertEquals(1, result.size)
        assertEquals(reintroduced, result[0])
    }

    // ============================================================================
    // MULTIPLE VIOLATIONS
    // ============================================================================

    @Test
    fun `findReintroductions returns all matching files when multiple migrated layouts reintroduced`() {
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        layoutDir.resolve("download_header.xml").writeText("<layout/>")
        layoutDir.resolve("download_list.xml").writeText("<layout/>")
        layoutDir.resolve("unrelated.xml").writeText("<layout/>")
        val migratedLayouts = setOf("download_header", "download_list", "download_item")
        val guardrail = ComposeMigrationGuardrail(layoutDir, migratedLayouts)

        val result = guardrail.findReintroductions()

        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "download_header.xml" })
        assertTrue(result.any { it.name == "download_list.xml" })
    }

    // ============================================================================
    // EXACT NAME MATCH (not substring)
    // ============================================================================

    @Test
    fun `findReintroductions does not match partial names`() {
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        // "reader_error_dialog.xml" should NOT trigger a violation for "reader_error"
        layoutDir.resolve("reader_error_dialog.xml").writeText("<layout/>")
        val guardrail = ComposeMigrationGuardrail(layoutDir, setOf("reader_error"))

        val result = guardrail.findReintroductions()

        assertEquals(emptyList<File>(), result)
    }

    // ============================================================================
    // EMPTY MIGRATED SET
    // ============================================================================

    @Test
    fun `findReintroductions returns empty list when migrated set is empty`() {
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        layoutDir.resolve("reader_error.xml").writeText("<layout/>")
        val guardrail = ComposeMigrationGuardrail(layoutDir, emptySet())

        val result = guardrail.findReintroductions()

        assertEquals(emptyList<File>(), result)
    }

    // ============================================================================
    // NON-XML FILES IGNORED
    // ============================================================================

    @Test
    fun `findReintroductions ignores non-xml files even if name matches migrated layout`() {
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        layoutDir.resolve("reader_error.kt").writeText("// not xml")
        layoutDir.resolve("reader_error").writeText("no extension")
        val guardrail = ComposeMigrationGuardrail(layoutDir, setOf("reader_error"))

        val result = guardrail.findReintroductions()

        assertEquals(emptyList<File>(), result)
    }
}
