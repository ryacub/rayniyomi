package eu.kanade.presentation.theme.cover

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

private const val MIN_TEXT_CONTRAST = 4.5

internal object CoverThemeColorUtils {
    fun bestOnColor(background: Color): Color {
        return Color(bestOnColorArgb(opaqueArgb(background)))
    }

    fun ensureReadable(foreground: Color, background: Color): Color {
        return Color(ensureReadableArgb(opaqueArgb(foreground), opaqueArgb(background)))
    }

    fun blend(color: Color, other: Color, ratio: Float): Color {
        return Color(ColorUtils.blendARGB(color.toArgb(), other.toArgb(), ratio.coerceIn(0f, 1f)))
    }

    internal fun bestOnColorArgb(background: Int): Int {
        val whiteContrast = ColorUtils.calculateContrast(android.graphics.Color.WHITE, background)
        val blackContrast = ColorUtils.calculateContrast(android.graphics.Color.BLACK, background)
        return if (whiteContrast >= blackContrast) android.graphics.Color.WHITE else android.graphics.Color.BLACK
    }

    internal fun ensureReadableArgb(foreground: Int, background: Int): Int {
        val contrast = ColorUtils.calculateContrast(foreground, background)
        if (contrast >= MIN_TEXT_CONTRAST) return foreground
        return bestOnColorArgb(background)
    }

    private fun opaqueArgb(color: Color): Int {
        return ColorUtils.setAlphaComponent(color.toArgb(), 255)
    }

    fun buildTokens(seed: Color, isDark: Boolean): CoverThemeTokens {
        val primary = if (isDark) blend(seed, Color.White, 0.22f) else blend(seed, Color.Black, 0.14f)
        val container = if (isDark) blend(primary, Color.White, 0.16f) else blend(primary, Color.White, 0.82f)

        val onPrimary = ensureReadable(bestOnColor(primary), primary)
        val onContainer = ensureReadable(bestOnColor(container), container)

        return CoverThemeTokens(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
        )
    }
}
