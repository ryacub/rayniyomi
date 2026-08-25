package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Captures page views into bitmaps for the curl transition.
 */
internal class PageCurlCapture(
    private val samplePixel: (Bitmap, Int, Int) -> Int = Bitmap::getPixel,
    private val isHardwareBitmap: (Bitmap) -> Boolean = ::hasHardwareConfig,
    private val logHardwareSkip: () -> Unit = {
        logcat(LogPriority.INFO) { "Skipped the page curl. The page holds a hardware bitmap." }
    },
) {

    // The capture logs the hardware skip one time for each coordinator. A log
    // for each page turn adds no information.
    private var loggedHardwareSkip = false

    private companion object {
        // The size of one side of the pixel grid sampled to detect a blank
        // capture.
        private const val BLANK_SAMPLE_GRID = 24
    }

    /**
     * Captures the current drawing of [source] into a bitmap.
     *
     * Returns null when the source has no size, when the source holds a
     * hardware bitmap, when the bitmap allocation fails, when the drawing
     * fails, or when the drawing produces no visible pixels.
     *
     * The proactive hardware check sees background drawables, ImageView
     * drawables, and LayerDrawable layers only. A view that paints a bitmap
     * through Canvas.drawBitmap with no Drawable stays undetected. The
     * IllegalArgumentException catch remains its backstop.
     */
    fun capture(source: View): Bitmap? {
        val width = source.width
        val height = source.height
        if (width <= 0 || height <= 0) {
            return null
        }
        if (!canCapture(source)) {
            if (!loggedHardwareSkip) {
                loggedHardwareSkip = true
                logHardwareSkip()
            }
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
     * Reports whether [source] can draw into a software canvas.
     *
     * Returns false when the view tree holds a hardware bitmap. A hardware
     * bitmap makes [android.view.View.draw] throw on a software canvas.
     */
    fun canCapture(source: View): Boolean = !hasHardwareContent(source)

    /**
     * Walks the view tree for a hardware bitmap. The walk sees background
     * drawables, ImageView drawables, and LayerDrawable layers only. A view
     * that paints a bitmap through Canvas.drawBitmap stays undetected.
     */
    private fun hasHardwareContent(view: View): Boolean {
        if (isHardwareDrawable(view.background)) return true
        if (view is ImageView && isHardwareDrawable(view.drawable)) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (hasHardwareContent(view.getChildAt(index))) return true
            }
        }
        return false
    }

    private fun isHardwareDrawable(drawable: Drawable?): Boolean = when (drawable) {
        null -> false
        is BitmapDrawable -> drawable.bitmap?.let(isHardwareBitmap) == true
        is LayerDrawable -> (0 until drawable.numberOfLayers).any {
            isHardwareDrawable(drawable.getDrawable(it))
        }
        else -> false
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

private fun hasHardwareConfig(bitmap: Bitmap): Boolean =
    bitmap.config == Bitmap.Config.HARDWARE
