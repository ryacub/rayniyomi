package eu.kanade.presentation.more.settings.widget

import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.colorscheme.CustomAccentContrastMode
import eu.kanade.presentation.theme.colorscheme.CustomAccentContrastWarning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomThemeAccentPreferenceWidgetTest {

    @Test
    fun `normalize accent seed enforces opaque alpha`() {
        val normalized = normalizeAccentSeed(0x004285F4)

        assertEquals(0xFF4285F4.toInt(), normalized)
    }

    @Test
    fun `picker apply returns normalized draft seed`() {
        val resolved = resolvePickerResultSeed(
            confirmSelection = true,
            currentSeed = UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET,
            draftSeed = 0x0000FF00,
        )

        assertEquals(0xFF00FF00.toInt(), resolved)
    }

    @Test
    fun `picker cancel keeps existing seed unchanged`() {
        val current = 0xFF1E88E5.toInt()
        val resolved = resolvePickerResultSeed(
            confirmSelection = false,
            currentSeed = current,
            draftSeed = 0x0000FF00,
        )

        assertEquals(current, resolved)
    }

    @Test
    fun `hsv conversion returns opaque seed and valid hsv bounds`() {
        val green = hsvToAccentSeed(hue = 120f, saturation = 1f, value = 1f)
        val redHsv = seedToHsv(0xFFFF0000.toInt())

        val alpha = (green ushr 24) and 0xFF
        assertEquals(0xFF, alpha)
        assertTrue(redHsv.hue in 0f..360f)
        assertTrue(redHsv.saturation in 0f..1f)
        assertTrue(redHsv.value in 0f..1f)
    }

    @Test
    fun `hsv conversion clamps invalid values to safe range`() {
        val clamped = hsvToAccentSeed(hue = 999f, saturation = -2f, value = 4f)
        val hsv = seedToHsv(clamped)

        assertTrue(hsv.hue in 0f..360f)
        assertTrue(hsv.saturation in 0f..1f)
        assertTrue(hsv.value in 0f..1f)
    }

    @Test
    fun `custom swatches contain unique colors`() {
        assertEquals(customAccentSwatches.size, customAccentSwatches.distinct().size)
        assertTrue(customAccentSwatches.isNotEmpty())
        assertNotEquals(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET, customAccentSwatches.first())
    }

    @Test
    fun `contrast warning summary includes affected modes in fixed order`() {
        val summary = formatCustomAccentContrastWarningSummary(
            warning = CustomAccentContrastWarning(
                setOf(CustomAccentContrastMode.DARK_AMOLED, CustomAccentContrastMode.LIGHT),
            ),
            warningSummaryFormat = "Warning: low contrast in %s",
            lightLabel = "Light",
            darkLabel = "Dark",
            amoledLabel = "Dark (AMOLED)",
        )

        assertEquals("Warning: low contrast in Light, Dark (AMOLED)", summary)
    }

    @Test
    fun `contrast warning summary is null when warning has no failing modes`() {
        val summary = formatCustomAccentContrastWarningSummary(
            warning = CustomAccentContrastWarning(emptySet()),
            warningSummaryFormat = "Warning: low contrast in %s",
            lightLabel = "Light",
            darkLabel = "Dark",
            amoledLabel = "Dark (AMOLED)",
        )

        assertEquals(null, summary)
    }
}
