package eu.kanade.tachiyomi.ui.reader

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

/**
 * Tests for ReaderConfigManager that don't require Android framework.
 *
 * Note: Most tests require Robolectric due to Android dependencies (Color, ColorMatrix,
 * WindowManager, Uri). Only pure calculation logic can be tested here.
 */
class ReaderConfigManagerTest {

    @Test
    fun `close stops preference collectors without cancelling parent scope`() = runTest {
        val readerPreferences = mockk<ReaderPreferences>()
        val basePreferences = mockk<BasePreferences>()
        val keepScreenOnPreference = FakePreference(true)

        stubReaderConfigPreferences(
            readerPreferences = readerPreferences,
            basePreferences = basePreferences,
            keepScreenOnPreference = keepScreenOnPreference,
        )

        val manager = ReaderConfigManager(
            readerPreferences = readerPreferences,
            basePreferences = basePreferences,
            scope = this,
            isNightMode = false,
            displayProfileLoader = { null },
        )
        advanceUntilIdle()

        assertEquals(true, manager.keepScreenOn.value)

        manager.close()
        keepScreenOnPreference.set(false)
        advanceUntilIdle()

        assertEquals(true, manager.keepScreenOn.value)
        assertEquals(true, coroutineContext[kotlinx.coroutines.Job]?.isActive)
    }

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

    private fun stubReaderConfigPreferences(
        readerPreferences: ReaderPreferences,
        basePreferences: BasePreferences,
        keepScreenOnPreference: Preference<Boolean> = FakePreference(true),
    ) {
        every { readerPreferences.readerTheme() } returns FakePreference(1)
        every { basePreferences.displayProfile() } returns FakePreference("")
        every { readerPreferences.cutoutShort() } returns FakePreference(true)
        every { readerPreferences.keepScreenOn() } returns keepScreenOnPreference
        every { readerPreferences.customBrightness() } returns FakePreference(false)
        every { readerPreferences.customBrightnessValue() } returns FakePreference(0)
        every { readerPreferences.grayscale() } returns FakePreference(false)
        every { readerPreferences.invertedColors() } returns FakePreference(false)
        every { readerPreferences.fullscreen() } returns FakePreference(true)
    }

    private class FakePreference<T>(
        initialValue: T,
    ) : Preference<T> {
        private val state = MutableStateFlow(initialValue)

        override fun key(): String = "fake"

        override fun get(): T = state.value

        override fun set(value: T) {
            state.value = value
        }

        override fun isSet(): Boolean = true

        override fun delete() = error("Not needed for this test")

        override fun defaultValue(): T = state.value

        override fun changes(): Flow<T> = state.asStateFlow()

        override fun stateIn(scope: kotlinx.coroutines.CoroutineScope): StateFlow<T> = state
    }
}
