package eu.kanade.presentation.theme.colorscheme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeContent
import kotlin.math.max
import kotlin.math.min

internal class CustomAccentColorScheme(
    private val context: Context,
    seed: Int,
) : BaseColorScheme() {

    private val normalizedSeed = seed or OPAQUE_ALPHA_MASK

    override val lightScheme = ensureReadableOrFallback(
        source = generateColorSchemeFromSeed(
            context = context,
            seed = normalizedSeed,
            dark = false,
        ),
        fallback = TachiyomiColorScheme.lightScheme,
    )

    override val darkScheme = ensureReadableOrFallback(
        source = generateColorSchemeFromSeed(
            context = context,
            seed = normalizedSeed,
            dark = true,
        ),
        fallback = TachiyomiColorScheme.darkScheme,
    )
}

private const val OPAQUE_ALPHA_MASK = -0x1000000
private const val MIN_CONTRAST_RATIO = 4.5

internal fun ensureReadableOrFallback(source: ColorScheme, fallback: ColorScheme): ColorScheme {
    if (isReadable(source)) return source

    val clamped = clampOnColorsForContrast(source)
    return if (isReadable(clamped)) clamped else fallback
}

internal fun isReadable(colorScheme: ColorScheme): Boolean {
    val pairs = listOf(
        colorScheme.primary to colorScheme.onPrimary,
        colorScheme.secondary to colorScheme.onSecondary,
        colorScheme.background to colorScheme.onBackground,
        colorScheme.surface to colorScheme.onSurface,
    )
    return pairs.all { (background, foreground) ->
        if (!background.hasValidComponents() || !foreground.hasValidComponents()) {
            return@all false
        }
        val ratio = contrastRatio(background, foreground)
        ratio.isFinite() && ratio >= MIN_CONTRAST_RATIO
    }
}

internal fun clampOnColorsForContrast(colorScheme: ColorScheme): ColorScheme {
    val onPrimary = bestReadableOnColor(colorScheme.primary)
    val onSecondary = bestReadableOnColor(colorScheme.secondary)
    val onBackground = bestReadableOnColor(colorScheme.background)
    val onSurface = bestReadableOnColor(colorScheme.surface)

    return colorScheme.copy(
        onPrimary = onPrimary,
        onSecondary = onSecondary,
        onBackground = onBackground,
        onSurface = onSurface,
    )
}

private fun bestReadableOnColor(background: Color): Color {
    val blackContrast = contrastRatio(background, Color.Black)
    val whiteContrast = contrastRatio(background, Color.White)
    return if (blackContrast >= whiteContrast) Color.Black else Color.White
}

internal fun contrastRatio(background: Color, foreground: Color): Double {
    val lighter = max(background.luminance(), foreground.luminance())
    val darker = min(background.luminance(), foreground.luminance())
    return (lighter + 0.05) / (darker + 0.05)
}

private fun Color.hasValidComponents(): Boolean {
    return red.isFinite() &&
        green.isFinite() &&
        blue.isFinite() &&
        alpha.isFinite() &&
        alpha >= 0.999f
}

internal fun resolveCustomSchemeContrast(sdkInt: Int, uiModeContrast: Float?): Double {
    return if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        uiModeContrast?.toDouble() ?: 0.0
    } else {
        0.0
    }
}

private fun generateColorSchemeFromSeed(context: Context, seed: Int, dark: Boolean): ColorScheme {
    val uiModeContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        context.getSystemService(UiModeManager::class.java)?.contrast
    } else {
        null
    }
    val contrast = resolveCustomSchemeContrast(
        sdkInt = Build.VERSION.SDK_INT,
        uiModeContrast = uiModeContrast,
    )
    val scheme = SchemeContent(
        Hct.fromInt(seed),
        dark,
        contrast,
    )
    val dynamicColors = MaterialDynamicColors()
    return ColorScheme(
        primary = dynamicColors.primary().getArgb(scheme).toComposeColor(),
        onPrimary = dynamicColors.onPrimary().getArgb(scheme).toComposeColor(),
        primaryContainer = dynamicColors.primaryContainer().getArgb(scheme).toComposeColor(),
        onPrimaryContainer = dynamicColors.onPrimaryContainer().getArgb(scheme).toComposeColor(),
        inversePrimary = dynamicColors.inversePrimary().getArgb(scheme).toComposeColor(),
        secondary = dynamicColors.secondary().getArgb(scheme).toComposeColor(),
        onSecondary = dynamicColors.onSecondary().getArgb(scheme).toComposeColor(),
        secondaryContainer = dynamicColors.secondaryContainer().getArgb(scheme).toComposeColor(),
        onSecondaryContainer = dynamicColors.onSecondaryContainer().getArgb(scheme).toComposeColor(),
        tertiary = dynamicColors.tertiary().getArgb(scheme).toComposeColor(),
        onTertiary = dynamicColors.onTertiary().getArgb(scheme).toComposeColor(),
        tertiaryContainer = dynamicColors.tertiaryContainer().getArgb(scheme).toComposeColor(),
        onTertiaryContainer = dynamicColors.onTertiaryContainer().getArgb(scheme).toComposeColor(),
        background = dynamicColors.background().getArgb(scheme).toComposeColor(),
        onBackground = dynamicColors.onBackground().getArgb(scheme).toComposeColor(),
        surface = dynamicColors.surface().getArgb(scheme).toComposeColor(),
        onSurface = dynamicColors.onSurface().getArgb(scheme).toComposeColor(),
        surfaceVariant = dynamicColors.surfaceVariant().getArgb(scheme).toComposeColor(),
        onSurfaceVariant = dynamicColors.onSurfaceVariant().getArgb(scheme).toComposeColor(),
        surfaceTint = dynamicColors.surfaceTint().getArgb(scheme).toComposeColor(),
        inverseSurface = dynamicColors.inverseSurface().getArgb(scheme).toComposeColor(),
        inverseOnSurface = dynamicColors.inverseOnSurface().getArgb(scheme).toComposeColor(),
        error = dynamicColors.error().getArgb(scheme).toComposeColor(),
        onError = dynamicColors.onError().getArgb(scheme).toComposeColor(),
        errorContainer = dynamicColors.errorContainer().getArgb(scheme).toComposeColor(),
        onErrorContainer = dynamicColors.onErrorContainer().getArgb(scheme).toComposeColor(),
        outline = dynamicColors.outline().getArgb(scheme).toComposeColor(),
        outlineVariant = dynamicColors.outlineVariant().getArgb(scheme).toComposeColor(),
        scrim = Color.Black,
        surfaceBright = dynamicColors.surfaceBright().getArgb(scheme).toComposeColor(),
        surfaceDim = dynamicColors.surfaceDim().getArgb(scheme).toComposeColor(),
        surfaceContainer = dynamicColors.surfaceContainer().getArgb(scheme).toComposeColor(),
        surfaceContainerHigh = dynamicColors.surfaceContainerHigh().getArgb(scheme).toComposeColor(),
        surfaceContainerHighest = dynamicColors.surfaceContainerHighest().getArgb(scheme).toComposeColor(),
        surfaceContainerLow = dynamicColors.surfaceContainerLow().getArgb(scheme).toComposeColor(),
        surfaceContainerLowest = dynamicColors.surfaceContainerLowest().getArgb(scheme).toComposeColor(),
    )
}

private fun Int.toComposeColor(): Color = Color(this)
