package eu.kanade.presentation.more.settings.widget

import eu.kanade.domain.ui.model.AppTheme
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppThemePreferenceWidgetTest {

    @Test
    fun `custom theme is visible in app theme options`() {
        val themes = availableAppThemes(isDynamicColorAvailable = true)

        assertTrue(themes.contains(AppTheme.CUSTOM))
    }

    @Test
    fun `monet is hidden when dynamic color is unavailable`() {
        val themes = availableAppThemes(isDynamicColorAvailable = false)

        assertFalse(themes.contains(AppTheme.MONET))
    }
}
