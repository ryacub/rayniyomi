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
        val tertiary = CoverThemeColorUtils.blend(primary, primaryContainer, 0.5f)
        val onTertiary = CoverThemeColorUtils.ensureReadable(CoverThemeColorUtils.bestOnColor(tertiary), tertiary)
        val surface = CoverThemeColorUtils.blend(colorScheme.surface, primary, 0.08f)
        val background = CoverThemeColorUtils.blend(colorScheme.background, primary, 0.05f)
        val onSurface = CoverThemeColorUtils.ensureReadable(colorScheme.onSurface, surface)
        val onBackground = CoverThemeColorUtils.ensureReadable(colorScheme.onBackground, background)
        val surfaceVariant = CoverThemeColorUtils.blend(colorScheme.surfaceVariant, primary, 0.12f)
        val onSurfaceVariant = CoverThemeColorUtils.ensureReadable(colorScheme.onSurfaceVariant, surfaceVariant)
        val outline = CoverThemeColorUtils.blend(colorScheme.outline, primary, 0.3f)

        return colorScheme.copy(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = onPrimaryContainer,
            surface = surface,
            onSurface = onSurface,
            background = background,
            onBackground = onBackground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            outline = outline,
            surfaceTint = primary,
        )
    }
}
