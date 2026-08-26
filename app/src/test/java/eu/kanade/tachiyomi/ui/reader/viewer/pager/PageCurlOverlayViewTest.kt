package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.view.animation.DecelerateInterpolator
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
 * Call-structure tests for [PageCurlOverlayView.drawFrame]. The entry point
 * is stateless, so no View is constructed and no pixels are checked.
 */
class PageCurlOverlayViewTest {

    private val width = 1080f
    private val height = 1920f

    private val canvas = mockk<Canvas>(relaxed = true)
    private val shadowPaint = mockk<Paint>(relaxed = true)

    @BeforeEach
    fun setUp() {
        // The renderer constructs a gradient per shadowed frame; the
        // playback collaborator constructs an interpolator per play.
        mockkConstructor(LinearGradient::class)
        mockkConstructor(DecelerateInterpolator::class)
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

        PageCurlOverlayView.drawFrame(
            canvas,
            from,
            to,
            0.5f,
            CurlDirection.FROM_RIGHT,
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

        PageCurlOverlayView.drawFrame(
            canvas,
            from,
            to,
            0.5f,
            CurlDirection.FROM_RIGHT,
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

        PageCurlOverlayView.drawFrame(
            canvas,
            from,
            to,
            0.5f,
            CurlDirection.FROM_RIGHT,
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

        PageCurlOverlayView.drawFrame(
            canvas,
            from,
            to,
            0.5f,
            CurlDirection.FROM_LEFT,
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

        PageCurlOverlayView.drawFrame(
            canvas,
            from,
            to,
            1f,
            CurlDirection.FROM_RIGHT,
            PageCurlRollMath.newVerts(),
            PageCurlRollMath.newColors(),
            shadowPaint,
        )

        verify(exactly = 0) { canvas.drawRect(any(), any(), any(), any(), any()) }
    }

    // R948: the overlay owns transition terminality through play tokens.
    @Test
    fun `a fresh play token is current until invalidated`() {
        val gate = CurlPlayGate()

        val token = gate.begin()

        gate.isCurrent(token) shouldBe true
        gate.invalidate()
        gate.isCurrent(token) shouldBe false
    }

    @Test
    fun `a newer play token supersedes an older one`() {
        val gate = CurlPlayGate()

        val first = gate.begin()
        val second = gate.begin()

        gate.isCurrent(first) shouldBe false
        gate.isCurrent(second) shouldBe true
    }

    // R948: the playback collaborator owns the terminality sequencing, so a
    // plain JUnit test drives it with a fake animator. A cancelled animator
    // fires its end synchronously inside cancel; that end must be suppressed
    // because invalidation lands first.
    @Test
    fun `abort suppresses a synchronous animation end`() {
        val animator = mockk<ValueAnimator>(relaxed = true)
        val listeners = mutableListOf<Animator.AnimatorListener>()
        every { animator.addListener(any()) } answers { listeners.add(firstArg()) }
        every { animator.cancel() } answers {
            listeners.forEach { it.onAnimationEnd(animator) }
        }
        val playback = PageCurlPlayback(newAnimator = { animator }, onUpdate = {})
        var ended = false

        playback.play(bitmap(), bitmap(), CurlDirection.FROM_RIGHT, 500L) { ended = true }
        playback.abort()

        ended shouldBe false
    }

    @Test
    fun `a finished play reports its end`() {
        val animator = mockk<ValueAnimator>(relaxed = true)
        val listeners = mutableListOf<Animator.AnimatorListener>()
        every { animator.addListener(any()) } answers { listeners.add(firstArg()) }
        val playback = PageCurlPlayback(newAnimator = { animator }, onUpdate = {})
        var ended = false

        playback.play(bitmap(), bitmap(), CurlDirection.FROM_RIGHT, 500L) { ended = true }
        listeners.single().onAnimationEnd(animator)

        ended shouldBe true
    }

    @Test
    fun `a newer play supersedes the older play's end`() {
        val first = mockk<ValueAnimator>(relaxed = true)
        val second = mockk<ValueAnimator>(relaxed = true)
        val firstListeners = mutableListOf<Animator.AnimatorListener>()
        every { first.addListener(any()) } answers { firstListeners.add(firstArg()) }
        val queue = ArrayDeque(listOf(first, second))
        val playback = PageCurlPlayback(newAnimator = { queue.removeFirst() }, onUpdate = {})
        var ended = false

        playback.play(bitmap(), bitmap(), CurlDirection.FROM_RIGHT, 500L) { ended = true }
        playback.play(bitmap(), bitmap(), CurlDirection.FROM_RIGHT, 500L) { }
        firstListeners.forEach { it.onAnimationEnd(first) }

        ended shouldBe false
    }

    companion object {
        private const val UNSHADED = 0xFFFFFFFF.toInt()
    }
}
