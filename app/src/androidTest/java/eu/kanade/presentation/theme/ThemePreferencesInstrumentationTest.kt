package eu.kanade.presentation.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.theme.colorscheme.CustomAccentColorScheme
import eu.kanade.presentation.theme.colorscheme.TachiyomiColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.preference.AndroidPreferenceStore

@RunWith(AndroidJUnit4::class)
class ThemePreferencesInstrumentationTest {

    private lateinit var context: Context
    private lateinit var uiPreferences: UiPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
        uiPreferences = UiPreferences(AndroidPreferenceStore(context, sharedPreferences))
    }

    @Test
    fun customAccentSeedPersistsChosenValue() {
        val selectedAccent = 0xFF4285F4.toInt()

        uiPreferences.customThemeAccentSeed().set(selectedAccent)

        assertEquals(selectedAccent, uiPreferences.customThemeAccentSeed().get())
    }

    @Test
    fun customAccentSeedResetUsesUnsetSentinel() {
        uiPreferences.customThemeAccentSeed().set(0xFF1E88E5.toInt())

        uiPreferences.customThemeAccentSeed().set(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET)

        assertEquals(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET, uiPreferences.customThemeAccentSeed().get())
    }

    @Test
    fun customThemeFallbackUsesDefaultSchemeWhenAccentUnset() {
        val resolved = resolveBaseColorScheme(
            appTheme = AppTheme.CUSTOM,
            customAccentSeed = UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET,
            context = context,
        )

        assertSame(TachiyomiColorScheme, resolved)
    }

    @Test
    fun customThemeWithSeedUsesGeneratedAccentScheme() {
        val resolved = resolveBaseColorScheme(
            appTheme = AppTheme.CUSTOM,
            customAccentSeed = 0xFF6750A4.toInt(),
            context = context,
        )

        assertTrue(resolved is CustomAccentColorScheme)
    }

    private companion object {
        const val PREFS_NAME = "theme_instrumentation_test_prefs"
    }
}
