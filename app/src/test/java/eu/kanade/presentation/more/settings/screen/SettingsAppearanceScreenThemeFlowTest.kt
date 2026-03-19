package eu.kanade.presentation.more.settings.screen

import eu.kanade.domain.ui.UiPreferences
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsAppearanceScreenThemeFlowTest {

    @Test
    fun `picker seed falls back to default when accent is unset`() {
        val resolved = resolveInitialCustomAccentPickerSeed(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET)

        assertEquals(SettingsAppearanceScreen.DEFAULT_CUSTOM_ACCENT_PICKER_SEED, resolved)
    }

    @Test
    fun `picker seed normalizes opaque alpha for persisted accents`() {
        val resolved = resolveInitialCustomAccentPickerSeed(0x00123456)

        assertEquals(0xFF123456.toInt(), resolved)
    }

    @Test
    fun `picker session increments deterministically`() {
        assertEquals(1, nextCustomAccentPickerSession(0))
        assertEquals(11, nextCustomAccentPickerSession(10))
    }
}
