package eu.kanade.domain.ui.model

import androidx.appcompat.app.AppCompatDelegate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemeModeTest {

    @Test
    fun `light mode maps to MODE_NIGHT_NO`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemeMode.LIGHT.toAppCompatDelegateMode())
    }

    @Test
    fun `dark mode maps to MODE_NIGHT_YES`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemeMode.DARK.toAppCompatDelegateMode())
    }

    @Test
    fun `system mode maps to MODE_NIGHT_FOLLOW_SYSTEM`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.SYSTEM.toAppCompatDelegateMode())
    }

    @Test
    fun `custom mode maps to MODE_NIGHT_FOLLOW_SYSTEM`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, ThemeMode.CUSTOM.toAppCompatDelegateMode())
    }
}
