package eu.kanade.tachiyomi.data.translation.renderer

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max

/**
 * Utility for fitting translated text within a bounding rectangle.
 * Uses binary search to find the largest font size that fits.
 */
object TextFitter {

    private const val MIN_FONT_SIZE = 6f
    private const val MAX_FONT_SIZE = 120f
    private const val PADDING_RATIO = 0.05f

    /**
     * Find the optimal font size and create a StaticLayout that fits within the given bounds.
     *
     * @param text The text to fit.
     * @param width Available width in pixels.
     * @param height Available height in pixels.
     * @param textPaint The paint to configure with the optimal size.
     * @return A pair of (fontSize, StaticLayout) that fits within bounds, or null if text can't fit.
     */
    fun fitText(
        text: String,
        width: Int,
        height: Int,
        textPaint: TextPaint,
    ): StaticLayout? {
        if (text.isBlank() || width <= 0 || height <= 0) return null

        val paddedWidth = (width * (1f - 2 * PADDING_RATIO)).toInt()
        val paddedHeight = (height * (1f - 2 * PADDING_RATIO)).toInt()
        if (paddedWidth <= 0 || paddedHeight <= 0) return null

        var low = MIN_FONT_SIZE
        var high = MAX_FONT_SIZE
        var bestSize = MIN_FONT_SIZE
        var bestLayout: StaticLayout? = null

        while (high - low > 0.5f) {
            val mid = (low + high) / 2f
            textPaint.textSize = mid

            val layout = createLayout(text, textPaint, paddedWidth)
            if (layout.height <= paddedHeight) {
                bestSize = mid
                bestLayout = layout
                low = mid
            } else {
                high = mid
            }
        }

        textPaint.textSize = bestSize
        return bestLayout ?: createLayout(text, textPaint, paddedWidth)
    }

    private fun createLayout(
        text: String,
        textPaint: TextPaint,
        width: Int,
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, max(1, width))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()
    }
}
