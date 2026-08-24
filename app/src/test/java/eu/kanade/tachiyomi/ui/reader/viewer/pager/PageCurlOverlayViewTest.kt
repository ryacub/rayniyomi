package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Call-structure tests for [PageCurlFrameRenderer]. The renderer is a plain
 * object, so no View is constructed and no pixels are checked.
 */
class PageCurlOverlayViewTest {

    private val width = 1080f
    private val height = 1920f

    private val canvas = mockk<Canvas>(relaxed = true)
    private val shadowPaint = mockk<Paint>(relaxed = true)

    @BeforeEach
    fun setUp() {
        // The renderer constructs a gradient per shadowed frame.
        mockkConstructor(LinearGradient::class)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun bitmap(): Bitmap {
        val bmp = mockk<Bitmap>(relaxed = true)
        every { bmp.width } returns width.toInt()
        every { bmp.height } returns height.toInt()
        return bmp
    }

    @Test
    fun `frame modulates the mesh with per vertex colors`() {
        val from = bitmap()
        val to = bitmap()
        val verts = PageCurlRollMath.newVerts()
        val colors = PageCurlRollMath.newColors()

        PageCurlFrameRenderer.drawFrame(
            canvas,
            from,
            to,
            0.5f,
            true,
            verts,
            colors,
            shadowPaint,
        )

        val captured = slot<IntArray>()
        verify {
            canvas.drawBitmapMesh(
                from,
                PageCurlRollMath.MESH_COLS,
                PageCurlRollMath.MESH_ROWS,
                verts,
                0,
                capture(captured),
                0,
                null,
            )
        }
        captured.captured.size shouldBe PageCurlRollMath.colorCount()
        (captured.captured.any { it != UNSHADED }) shouldBe true
    }

    @Test
    fun `shadow is drawn between the incoming page and the mesh`() {
        val from = bitmap()
        val to = bitmap()

        PageCurlFrameRenderer.drawFrame(
            canvas,
            from,
            to,
            0.5f,
            true,
            PageCurlRollMath.newVerts(),
            PageCurlRollMath.newColors(),
            shadowPaint,
        )

        verifyOrder {
            canvas.drawBitmap(to, 0f, 0f, null)
            canvas.drawRect(any(), any(), any(), any(), any())
            canvas.drawBitmapMesh(from, any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `shadow band hugs the fold line for right curls`() {
        val from = bitmap()
        val to = bitmap()

        PageCurlFrameRenderer.drawFrame(
            canvas,
            from,
            to,
            0.5f,
            true,
            PageCurlRollMath.newVerts(),
            PageCurlRollMath.newColors(),
            shadowPaint,
        )

        val left = slot<Float>()
        val top = slot<Float>()
        val right = slot<Float>()
        val bottom = slot<Float>()
        verify { canvas.drawRect(capture(left), capture(top), capture(right), capture(bottom), any()) }

        val tangent = PageCurlRollMath.tangentX(width, 0.5f)
        left.captured shouldBe tangent
        right.captured shouldBe tangent + width * PageCurlRollMath.CAST_SHADOW_WIDTH_FRACTION
        bottom.captured shouldBe height
    }

    @Test
    fun `shadow band mirrors for left curls`() {
        val from = bitmap()
        val to = bitmap()

        PageCurlFrameRenderer.drawFrame(
            canvas,
            from,
            to,
            0.5f,
            false,
            PageCurlRollMath.newVerts(),
            PageCurlRollMath.newColors(),
            shadowPaint,
        )

        val left = slot<Float>()
        val right = slot<Float>()
        verify { canvas.drawRect(capture(left), any(), capture(right), any(), any()) }

        val tangent = PageCurlRollMath.tangentX(width, 0.5f)
        left.captured shouldBe width - tangent - width * PageCurlRollMath.CAST_SHADOW_WIDTH_FRACTION
        right.captured shouldBe width - tangent
    }

    @Test
    fun `no shadow rect once the fold clears`() {
        val from = bitmap()
        val to = bitmap()

        PageCurlFrameRenderer.drawFrame(
            canvas,
            from,
            to,
            1f,
            true,
            PageCurlRollMath.newVerts(),
            PageCurlRollMath.newColors(),
            shadowPaint,
        )

        verify(exactly = 0) { canvas.drawRect(any(), any(), any(), any(), any()) }
    }

    companion object {
        private const val UNSHADED = 0xFFFFFFFF.toInt()
    }
}
