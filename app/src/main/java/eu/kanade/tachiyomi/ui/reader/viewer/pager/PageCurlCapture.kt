package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Captures page views into bitmaps for the curl transition.
 */
internal class PageCurlCapture(
    private val samplePixel: (Bitmap, Int, Int) -> Int = Bitmap::getPixel,
) {

    private companion object {
        // The size of one side of the pixel grid sampled to detect a blank
        // capture.
        private const val BLANK_SAMPLE_GRID = 24
    }

    /**
     * Captures the current drawing of [source] into a bitmap.
     *
     * Returns null when the source has no size, when the bitmap allocation
     * fails, when the drawing fails, or when the drawing produces no visible
     * pixels.
     */
    fun capture(source: View): Bitmap? {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) {
            return null
        }
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            source.draw(canvas)
            if (hasVisiblePixels(bitmap)) {
                bitmap
            } else {
                bitmap.recycle()
                logcat(LogPriority.WARN) { "Captured a blank page bitmap for the curl" }
                null
            }
        } catch (e: OutOfMemoryError) {
            logcat(LogPriority.ERROR, e) { "Failed to capture the page bitmap for the curl" }
            null
        } catch (e: IllegalStateException) {
            null
        } catch (e: IllegalArgumentException) {
            // A hardware bitmap cannot draw into a software canvas.
            logcat(LogPriority.WARN, e) { "Failed to capture the page bitmap for the curl" }
            null
        }
    }

    /**
     * Samples a coarse grid of pixels and reports whether any of them holds a
     * color other than fully transparent.
     */
    private fun hasVisiblePixels(bitmap: Bitmap): Boolean {
        val lastX = maxOf(bitmap.width - 1, 0)
        val lastY = maxOf(bitmap.height - 1, 0)
        for (row in 0 until BLANK_SAMPLE_GRID) {
            val y = row * lastY / (BLANK_SAMPLE_GRID - 1)
            for (column in 0 until BLANK_SAMPLE_GRID) {
                val x = column * lastX / (BLANK_SAMPLE_GRID - 1)
                if (samplePixel(bitmap, x, y) != Color.TRANSPARENT) return true
            }
        }
        return false
    }
}
