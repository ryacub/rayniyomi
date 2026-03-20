package eu.kanade.domain.ui.model

import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
    CUSTOM,
}

fun setAppCompatDelegateThemeMode(themeMode: ThemeMode) {
    AppCompatDelegate.setDefaultNightMode(themeMode.toAppCompatDelegateMode())
}

fun ThemeMode.toAppCompatDelegateMode(): Int = when (this) {
    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
    // Custom app palette still follows the system day/night mode.
    ThemeMode.SYSTEM, ThemeMode.CUSTOM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
}
