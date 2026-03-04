package xyz.rayniyomi.plugin.lightnovel.theme

import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.google.android.material.progressindicator.LinearProgressIndicator

private const val MIN_TEXT_CONTRAST = 4.5

internal data class CoverAccentTokens(
    val accent: Int,
    val onAccent: Int,
    val surface: Int,
    val onSurface: Int,
)

internal object CoverAccentTheme {
    fun tokensFor(seed: Int, darkMode: Boolean): CoverAccentTokens {
        val accent = if (darkMode) blend(seed, Color.WHITE, 0.22f) else blend(seed, Color.BLACK, 0.14f)
        val surface = if (darkMode) blend(accent, Color.WHITE, 0.16f) else blend(accent, Color.WHITE, 0.86f)
        val onAccent = readableForeground(accent)
        val onSurface = readableForeground(surface)

        return CoverAccentTokens(
            accent = accent,
            onAccent = ensureReadable(onAccent, accent),
            surface = surface,
            onSurface = ensureReadable(onSurface, surface),
        )
    }

    fun applyButton(button: Button, tokens: CoverAccentTokens) {
        button.backgroundTintList = ColorStateList.valueOf(tokens.accent)
        button.setTextColor(tokens.onAccent)
    }

    fun applyProgressBar(progressBar: ProgressBar, tokens: CoverAccentTokens) {
        val tint = ColorStateList.valueOf(tokens.accent)
        progressBar.progressTintList = tint
        progressBar.indeterminateTintList = tint
        if (progressBar is LinearProgressIndicator) {
            progressBar.trackColor = blend(tokens.accent, tokens.surface, 0.8f)
        }
    }

    fun applySurface(textView: TextView, tokens: CoverAccentTokens) {
        textView.setBackgroundColor(tokens.surface)
        textView.setTextColor(tokens.onSurface)
        ViewCompat.setElevation(textView, 2f)
    }

    private fun readableForeground(background: Int): Int {
        val whiteContrast = ColorUtils.calculateContrast(Color.WHITE, background)
        val blackContrast = ColorUtils.calculateContrast(Color.BLACK, background)
        return if (whiteContrast >= blackContrast) Color.WHITE else Color.BLACK
    }

    private fun ensureReadable(foreground: Int, background: Int): Int {
        val contrast = ColorUtils.calculateContrast(foreground, background)
        if (contrast >= MIN_TEXT_CONTRAST) return foreground
        return readableForeground(background)
    }

    private fun blend(start: Int, end: Int, ratio: Float): Int {
        return ColorUtils.blendARGB(start, end, ratio.coerceIn(0f, 1f))
    }
}
