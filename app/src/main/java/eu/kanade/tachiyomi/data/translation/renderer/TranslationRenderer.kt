package eu.kanade.tachiyomi.data.translation.renderer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.TextPaint
import eu.kanade.tachiyomi.data.translation.NormalizedRect
import eu.kanade.tachiyomi.data.translation.TranslationResult
import eu.kanade.tachiyomi.data.translation.engine.ImageFormatUtil
import java.io.ByteArrayOutputStream

/**
 * Renders translated text overlays onto manga page images.
 *
 * Takes an original image and a [TranslationResult], whites out the detected
 * text regions (using sampled edge colors), and draws the translated text on top.
 */
object TranslationRenderer {

    private const val PADDING_PX = 4

    /**
     * Render translation overlays on the source image.
     *
     * @param imageBytes Original image bytes.
     * @param result Translation result with text regions.
     * @return New image bytes with translations overlaid, in the same format.
     */
    fun render(imageBytes: ByteArray, result: TranslationResult): ByteArray {
        if (result.regions.isEmpty()) return imageBytes

        val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: return imageBytes
        val mutable = original.copy(Bitmap.Config.ARGB_8888, true)
        original.recycle()

        if (mutable == null) return imageBytes

        val canvas = Canvas(mutable)
        val fillPaint = Paint().apply { style = Paint.Style.FILL }
        val textPaint = TextPaint().apply {
            isAntiAlias = true
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        val imgWidth = mutable.width
        val imgHeight = mutable.height

        for (region in result.regions) {
            val pixelRect = toPixelRect(region.bounds, imgWidth, imgHeight)
            if (pixelRect.width() <= 0 || pixelRect.height() <= 0) continue

            // Sample edge color for fill
            fillPaint.color = sampleEdgeColor(mutable, pixelRect)

            // White out the original text region
            canvas.drawRect(
                pixelRect.left.toFloat(),
                pixelRect.top.toFloat(),
                pixelRect.right.toFloat(),
                pixelRect.bottom.toFloat(),
                fillPaint,
            )

            // Determine text color based on fill brightness
            textPaint.color = contrastColor(fillPaint.color)

            // Fit and draw translated text
            val layout = TextFitter.fitText(
                text = region.translatedText,
                width = pixelRect.width(),
                height = pixelRect.height(),
                textPaint = textPaint,
            ) ?: continue

            canvas.save()
            val paddingX = (pixelRect.width() * 0.05f)
            val paddingY = (pixelRect.height() - layout.height) / 2f
            canvas.translate(
                pixelRect.left + paddingX,
                pixelRect.top + paddingY.coerceAtLeast(0f),
            )
            layout.draw(canvas)
            canvas.restore()
        }

        val output = ByteArrayOutputStream()
        val format = ImageFormatUtil.detectCompressFormat(imageBytes)
        mutable.compress(format, 90, output)
        mutable.recycle()
        return output.toByteArray()
    }

    private fun toPixelRect(bounds: NormalizedRect, imgWidth: Int, imgHeight: Int): Rect {
        return Rect(
            (bounds.left * imgWidth).toInt().coerceIn(0, imgWidth),
            (bounds.top * imgHeight).toInt().coerceIn(0, imgHeight),
            (bounds.right * imgWidth).toInt().coerceIn(0, imgWidth),
            (bounds.bottom * imgHeight).toInt().coerceIn(0, imgHeight),
        )
    }

    /**
     * Sample the average color from the edges of the rectangle.
     * This gives us the bubble background color rather than hardcoding white.
     */
    private fun sampleEdgeColor(bitmap: Bitmap, rect: Rect): Int {
        var rTotal = 0L
        var gTotal = 0L
        var bTotal = 0L
        var count = 0

        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val right = (rect.right - 1).coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val bottom = (rect.bottom - 1).coerceIn(0, bitmap.height - 1)

        // Sample top and bottom edges
        for (x in left..right step 2) {
            samplePixel(bitmap, x, top).let { (r, g, b) ->
                rTotal += r
                gTotal += g
                bTotal += b
                count++
            }
            samplePixel(bitmap, x, bottom).let { (r, g, b) ->
                rTotal += r
                gTotal += g
                bTotal += b
                count++
            }
        }
        // Sample left and right edges
        for (y in top..bottom step 2) {
            samplePixel(bitmap, left, y).let { (r, g, b) ->
                rTotal += r
                gTotal += g
                bTotal += b
                count++
            }
            samplePixel(bitmap, right, y).let { (r, g, b) ->
                rTotal += r
                gTotal += g
                bTotal += b
                count++
            }
        }

        if (count == 0) return Color.WHITE

        return Color.rgb(
            (rTotal / count).toInt().coerceIn(0, 255),
            (gTotal / count).toInt().coerceIn(0, 255),
            (bTotal / count).toInt().coerceIn(0, 255),
        )
    }

    private fun samplePixel(bitmap: Bitmap, x: Int, y: Int): Triple<Int, Int, Int> {
        val pixel = bitmap.getPixel(x, y)
        return Triple(Color.red(pixel), Color.green(pixel), Color.blue(pixel))
    }

    /**
     * Return black or white text color depending on background brightness.
     */
    private fun contrastColor(backgroundColor: Int): Int {
        val luminance = (
            0.299 * Color.red(backgroundColor) +
                0.587 * Color.green(backgroundColor) +
                0.114 * Color.blue(backgroundColor)
            ) / 255.0
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
}
