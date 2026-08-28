package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test

class PageCurlBackFaceRendererTest {

    private val width = 1080
    private val height = 1920
    private val progress = 0.5f
    private val canvas = mockk<Canvas>(relaxed = true)
    private val softenPaint = mockk<Paint>(relaxed = true)
    private val bitmap = mockk<Bitmap>(relaxed = true) {
        every { width } returns this@PageCurlBackFaceRendererTest.width
        every { height } returns this@PageCurlBackFaceRendererTest.height
    }

    @Test
    fun `right curl clips and mirrors the back face`() {
        val drawing = PageCurlRollMath.foldBackDrawing(
            width.toFloat(),
            progress,
            CurlDirection.FROM_RIGHT,
        )!!

        PageCurlBackFaceRenderer.draw(
            canvas,
            bitmap,
            progress,
            CurlDirection.FROM_RIGHT,
            softenPaint,
        )

        verifyOrder {
            canvas.save()
            canvas.clipRect(
                drawing.clipSpan.start,
                0f,
                drawing.clipSpan.endInclusive,
                height.toFloat(),
            )
            canvas.scale(-1f, 1f, drawing.creaseX, 0f)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            canvas.drawRect(
                drawing.clipSpan.start,
                0f,
                drawing.clipSpan.endInclusive,
                height.toFloat(),
                softenPaint,
            )
            canvas.restore()
        }
    }

    @Test
    fun `left curl clips and mirrors the back face`() {
        val drawing = PageCurlRollMath.foldBackDrawing(
            width.toFloat(),
            progress,
            CurlDirection.FROM_LEFT,
        )!!

        PageCurlBackFaceRenderer.draw(
            canvas,
            bitmap,
            progress,
            CurlDirection.FROM_LEFT,
            softenPaint,
        )

        verifyOrder {
            canvas.save()
            canvas.clipRect(
                drawing.clipSpan.start,
                0f,
                drawing.clipSpan.endInclusive,
                height.toFloat(),
            )
            canvas.scale(-1f, 1f, drawing.creaseX, 0f)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            canvas.drawRect(
                drawing.clipSpan.start,
                0f,
                drawing.clipSpan.endInclusive,
                height.toFloat(),
                softenPaint,
            )
            canvas.restore()
        }
    }

    @Test
    fun `renderer does not draw before the back face appears`() {
        PageCurlBackFaceRenderer.draw(
            canvas,
            bitmap,
            0f,
            CurlDirection.FROM_RIGHT,
            softenPaint,
        )

        verify(exactly = 0) { canvas.save() }
        verify(exactly = 0) { canvas.drawBitmap(any<Bitmap>(), any<Float>(), any<Float>(), any()) }
    }
}
