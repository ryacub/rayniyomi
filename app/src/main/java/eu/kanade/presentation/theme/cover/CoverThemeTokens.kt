package eu.kanade.presentation.theme.cover

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

data class CoverThemeTokens(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
) {
    fun applyTo(colorScheme: ColorScheme): ColorScheme {
        return colorScheme.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            surfaceTint = primary,
        )
    }
}
