package eu.kanade.tachiyomi.ui.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for ReaderConfigManager that don't require Android framework.
 *
 * Note: Most tests require Robolectric due to Android dependencies (Color, ColorMatrix,
 * WindowManager, Uri). Only pure calculation logic can be tested here.
 */
class ReaderConfigManagerTest {

    @Test
    fun `calculateBrightness returns percentage for positive values`() {
        assertEquals(0.01f, ReaderConfigManager.calculateBrightness(1))
        assertEquals(0.5f, ReaderConfigManager.calculateBrightness(50))
        assertEquals(0.75f, ReaderConfigManager.calculateBrightness(75))
        assertEquals(1.0f, ReaderConfigManager.calculateBrightness(100))
    }

    @Test
    fun `calculateBrightness returns minimum for negative values`() {
        assertEquals(0.01f, ReaderConfigManager.calculateBrightness(-1))
        assertEquals(0.01f, ReaderConfigManager.calculateBrightness(-50))
        assertEquals(0.01f, ReaderConfigManager.calculateBrightness(-75))
    }

    @Test
    fun `calculateBrightness returns system default for zero`() {
        assertEquals(-1.0f, ReaderConfigManager.calculateBrightness(0))
    }

    @Test
    fun `calculateBrightness handles boundary values correctly`() {
        // Just above zero
        assertEquals(0.01f, ReaderConfigManager.calculateBrightness(1))
        // Just below zero
        assertEquals(0.01f, ReaderConfigManager.calculateBrightness(-1))
        // Maximum value
        assertEquals(1.0f, ReaderConfigManager.calculateBrightness(100))
        // Below minimum (still returns minimum)
        assertEquals(0.01f, ReaderConfigManager.calculateBrightness(-100))
    }
}
