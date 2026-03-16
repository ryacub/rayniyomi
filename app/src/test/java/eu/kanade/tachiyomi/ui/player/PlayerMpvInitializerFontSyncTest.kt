package eu.kanade.tachiyomi.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerMpvInitializerFontSyncTest {

    @Test
    fun `missing source directory produces no copy or delete actions`() {
        val destination = listOf(
            font("kept.ttf", 100),
            font("stale.otf", 200),
        )

        val plan = computeFontSyncPlan(
            sourceFiles = null,
            destinationFiles = destination,
        )

        assertFalse(plan.sourceAvailable)
        assertTrue(plan.filesToCopy.isEmpty())
        assertTrue(plan.unchangedFiles.isEmpty())
        assertTrue(plan.staleFilesToDelete.isEmpty())
    }

    @Test
    fun `new source font is copied`() {
        val source = listOf(
            font("new.ttf", 123),
        )

        val plan = computeFontSyncPlan(
            sourceFiles = source,
            destinationFiles = emptyList(),
        )

        assertTrue(plan.sourceAvailable)
        assertEquals(setOf("new.ttf"), plan.filesToCopy)
        assertTrue(plan.unchangedFiles.isEmpty())
        assertTrue(plan.staleFilesToDelete.isEmpty())
    }

    @Test
    fun `same name and size font is skipped as unchanged`() {
        val source = listOf(font("same.ttf", 500))
        val destination = listOf(font("same.ttf", 500))

        val plan = computeFontSyncPlan(
            sourceFiles = source,
            destinationFiles = destination,
        )

        assertTrue(plan.filesToCopy.isEmpty())
        assertEquals(setOf("same.ttf"), plan.unchangedFiles)
        assertTrue(plan.staleFilesToDelete.isEmpty())
    }

    @Test
    fun `destination stale font absent in source is deleted`() {
        val source = listOf(font("current.ttf", 10))
        val destination = listOf(
            font("current.ttf", 10),
            font("stale.otf", 99),
        )

        val plan = computeFontSyncPlan(
            sourceFiles = source,
            destinationFiles = destination,
        )

        assertEquals(setOf("current.ttf"), plan.unchangedFiles)
        assertEquals(setOf("stale.otf"), plan.staleFilesToDelete)
        assertTrue(plan.filesToCopy.isEmpty())
    }

    @Test
    fun `non-font destination file is untouched by stale cleanup`() {
        val source = listOf(font("current.ttf", 10))
        val destination = listOf(
            font("current.ttf", 10),
            file("readme.txt", 5),
        )

        val plan = computeFontSyncPlan(
            sourceFiles = source,
            destinationFiles = destination,
        )

        assertEquals(setOf("current.ttf"), plan.unchangedFiles)
        assertTrue(plan.staleFilesToDelete.isEmpty())
        assertTrue(plan.filesToCopy.isEmpty())
    }

    private fun font(name: String, length: Long): FontFileDescriptor = FontFileDescriptor(name, length)
    private fun file(name: String, length: Long): FontFileDescriptor = FontFileDescriptor(name, length)
}
