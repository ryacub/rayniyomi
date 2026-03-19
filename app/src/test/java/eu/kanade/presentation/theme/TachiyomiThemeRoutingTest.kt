package eu.kanade.presentation.theme

import android.content.Context
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.colorscheme.CustomAccentColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TachiyomiThemeRoutingTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `custom theme uses default scheme when accent seed is unset`() {
        val resolved = resolveBaseColorScheme(
            appTheme = AppTheme.CUSTOM,
            customAccentSeed = UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET,
            context = context,
        )

        assertSame(TachiyomiColorScheme, resolved)
    }

    @Test
    fun `custom theme uses generated accent scheme when seed is set`() {
        val resolved = resolveBaseColorScheme(
            appTheme = AppTheme.CUSTOM,
            customAccentSeed = 0xFF4285F4.toInt(),
            context = context,
        )

        assertTrue(resolved is CustomAccentColorScheme)
    }

    @Test
    fun `default theme remains mapped to tachiyomi scheme`() {
        val resolved = resolveBaseColorScheme(
            appTheme = AppTheme.DEFAULT,
            customAccentSeed = 0xFF4285F4.toInt(),
            context = context,
        )

        assertSame(TachiyomiColorScheme, resolved)
    }
}
