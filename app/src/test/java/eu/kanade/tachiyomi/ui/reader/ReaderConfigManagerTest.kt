package eu.kanade.tachiyomi.ui.reader

import android.graphics.Color
import android.view.WindowManager
import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderConfigManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var readerPreferences: ReaderPreferences
    private lateinit var basePreferences: BasePreferences
    private lateinit var configManager: ReaderConfigManager

    private lateinit var readerThemeFlow: MutableStateFlow<Int>
    private lateinit var displayProfileFlow: MutableStateFlow<String>
    private lateinit var cutoutShortFlow: MutableStateFlow<Boolean>
    private lateinit var keepScreenOnFlow: MutableStateFlow<Boolean>
    private lateinit var customBrightnessFlow: MutableStateFlow<Boolean>
    private lateinit var customBrightnessValueFlow: MutableStateFlow<Int>
    private lateinit var grayscaleFlow: MutableStateFlow<Boolean>
    private lateinit var invertedColorsFlow: MutableStateFlow<Boolean>
    private lateinit var fullscreenFlow: MutableStateFlow<Boolean>

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock Android framework classes
        mockkStatic(Color::class)
        every { Color.BLACK } returns -16777216
        every { Color.WHITE } returns -1
        every { Color.rgb(any(), any(), any()) } returns -2039584 // Gray color

        // Initialize flows
        readerThemeFlow = MutableStateFlow(1) // Default: Black
        displayProfileFlow = MutableStateFlow("")
        cutoutShortFlow = MutableStateFlow(false)
        keepScreenOnFlow = MutableStateFlow(false)
        customBrightnessFlow = MutableStateFlow(false)
        customBrightnessValueFlow = MutableStateFlow(0)
        grayscaleFlow = MutableStateFlow(false)
        invertedColorsFlow = MutableStateFlow(false)
        fullscreenFlow = MutableStateFlow(false)

        // Mock preferences
        readerPreferences = mockk {
            every { readerTheme() } returns mockPreference(readerThemeFlow)
            every { cutoutShort() } returns mockPreference(cutoutShortFlow)
            every { keepScreenOn() } returns mockPreference(keepScreenOnFlow)
            every { customBrightness() } returns mockPreference(customBrightnessFlow)
            every { customBrightnessValue() } returns mockPreference(customBrightnessValueFlow)
            every { grayscale() } returns mockPreference(grayscaleFlow)
            every { invertedColors() } returns mockPreference(invertedColorsFlow)
            every { fullscreen() } returns mockPreference(fullscreenFlow)
        }

        basePreferences = mockk {
            every { displayProfile() } returns mockPreference(displayProfileFlow)
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createConfigManager(scope: TestScope): ReaderConfigManager {
        return ReaderConfigManager(
            readerPreferences = readerPreferences,
            basePreferences = basePreferences,
            scope = scope,
            isNightMode = false,
        )
    }

    private fun <T> mockPreference(flow: MutableStateFlow<T>): Preference<T> {
        return mockk {
            every { changes() } returns flow
            every { get() } answers { flow.value }
        }
    }

    @Test
    fun `cutoutShort updates state`() = runTest {
        configManager = createConfigManager(this)
        advanceUntilIdle()

        cutoutShortFlow.value = true
        advanceUntilIdle()

        assertEquals(true, configManager.cutoutShort.first())
    }

    @Test
    fun `keepScreenOn updates state`() = runTest {
        configManager = createConfigManager(this)
        advanceUntilIdle()

        keepScreenOnFlow.value = true
        advanceUntilIdle()

        assertEquals(true, configManager.keepScreenOn.first())
    }

    @Test
    fun `customBrightness enabled updates state`() = runTest {
        configManager = createConfigManager(this)
        advanceUntilIdle()

        customBrightnessFlow.value = true
        advanceUntilIdle()

        assertEquals(true, configManager.customBrightnessEnabled.first())
    }

    @Test
    fun `customBrightness disabled resets value to 0`() = runTest {
        configManager = createConfigManager(this)
        advanceUntilIdle()

        customBrightnessFlow.value = true
        customBrightnessValueFlow.value = 50
        advanceUntilIdle()

        assertEquals(50, configManager.customBrightnessValue.first())

        customBrightnessFlow.value = false
        advanceUntilIdle()

        assertEquals(0, configManager.customBrightnessValue.first())
    }

    @Test
    fun `customBrightnessValue updates when enabled`() = runTest {
        configManager = createConfigManager(this)
        advanceUntilIdle()

        customBrightnessFlow.value = true
        advanceUntilIdle()

        customBrightnessValueFlow.value = 75
        advanceUntilIdle()

        assertEquals(75, configManager.customBrightnessValue.first())
    }

    @Test
    fun `grayscale updates layerPaint`() = runTest {
        configManager = createConfigManager(this)
        advanceUntilIdle()

        grayscaleFlow.value = true
        advanceUntilIdle()

        assertNotNull(configManager.layerPaint.first())
    }

    @Test
    fun `invertedColors updates layerPaint`() = runTest {
        configManager = createConfigManager(this)
        advanceUntilIdle()

        invertedColorsFlow.value = true
        advanceUntilIdle()

        assertNotNull(configManager.layerPaint.first())
    }

    @Test
    fun `grayscale and invertedColors both false clears layerPaint`() = runTest {
        configManager = createConfigManager(this)
        advanceUntilIdle()

        grayscaleFlow.value = true
        advanceUntilIdle()
        assertNotNull(configManager.layerPaint.first())

        grayscaleFlow.value = false
        advanceUntilIdle()

        assertNull(configManager.layerPaint.first())
    }

    @Test
    fun `fullscreen updates state`() = runTest {
        configManager = createConfigManager(this)
        advanceUntilIdle()

        fullscreenFlow.value = true
        advanceUntilIdle()

        assertEquals(true, configManager.fullscreen.first())
    }

    @Test
    fun `calculateReaderBrightness returns correct value for positive input`() = runTest {
        configManager = createConfigManager(this)
        val brightness = configManager.calculateReaderBrightness(50)
        assertEquals(0.5f, brightness, 0.01f)
    }

    @Test
    fun `calculateReaderBrightness returns minimum for negative input`() = runTest {
        configManager = createConfigManager(this)
        val brightness = configManager.calculateReaderBrightness(-50)
        assertEquals(0.01f, brightness, 0.01f)
    }

    @Test
    fun `calculateReaderBrightness returns system default for zero`() = runTest {
        configManager = createConfigManager(this)
        val brightness = configManager.calculateReaderBrightness(0)
        assertEquals(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE, brightness, 0.01f)
    }

    @Test
    fun `calculateReaderBrightness returns max for 100`() = runTest {
        configManager = createConfigManager(this)
        val brightness = configManager.calculateReaderBrightness(100)
        assertEquals(1.0f, brightness, 0.01f)
    }
}
