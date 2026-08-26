package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** Executes the Canvas operations for the visible back of the curled page. */
internal object PageCurlBackFaceRenderer {

    fun draw(
        canvas: Canvas,
        bitmap: Bitmap,
        progress: Float,
        direction: CurlDirection,
        softenPaint: Paint,
    ) {
        val bitmapWidth = bitmap.width.toFloat()
        val drawing = PageCurlRollMath.foldBackDrawing(bitmapWidth, progress, direction) ?: return
        val left = drawing.clipSpan.start
        val right = drawing.clipSpan.endInclusive
        val bitmapHeight = bitmap.height.toFloat()

        canvas.save()
        canvas.clipRect(left, 0f, right, bitmapHeight)
        when (drawing.transform) {
            FoldBackTransform.MIRROR -> canvas.scale(-1f, 1f, drawing.creaseX, 0f)
            FoldBackTransform.TRANSLATE -> canvas.translate(2f * drawing.creaseX - bitmapWidth, 0f)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.drawRect(left, 0f, right, bitmapHeight, softenPaint)
        canvas.restore()
    }
}
