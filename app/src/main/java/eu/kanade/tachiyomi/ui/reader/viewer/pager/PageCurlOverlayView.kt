package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible

/**
 * Draws the page curl transition on top of the pager.
 *
 * The view draws nothing until [playCurl] runs. The caller starts
 * transitions with [playCurl] and ends them with [abortAndHide].
 */
class PageCurlOverlayView(context: Context) : View(context) {

    private companion object {
        // Near-white wash over the mirrored strip so the back reads as paper
        // rather than as a mirrored copy of the front.
        private val BACK_SOFTEN_COLOR = Color.argb(90, 255, 255, 255)
    }
    private var animator: ValueAnimator? = null
    private var fromBitmap: Bitmap? = null
    private var toBitmap: Bitmap? = null
    private var direction = CurlDirection.FROM_RIGHT
    private var progress = 0f
    private val playGate = CurlPlayGate()

    private val verts = FloatArray(PageCurlRollMath.vertCount())
    private val meshColors = PageCurlRollMath.newColors()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backSoftenPaint = Paint().apply { color = BACK_SOFTEN_COLOR }

    init {
        isVisible = false
    }

    /**
     * Plays the curl transition from [from] to [to].
     *
     * The animator invokes [onEnd] exactly once when it finishes. An abort
     * or a newer play supersedes this play, and [onEnd] never runs.
     */
    fun playCurl(
        from: Bitmap,
        to: Bitmap,
        direction: CurlDirection,
        durationMs: Long,
        onEnd: () -> Unit,
    ) {
        val token = playGate.begin()
        fromBitmap = from
        toBitmap = to
        this.direction = direction
        progress = 0f
        isVisible = true

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                if (playGate.isCurrent(token)) onEnd()
            }
        }
        this.animator = animator
        animator.start()
    }

    /**
     * Stops the running curl animator, if any. Invalidation comes first:
     * cancelling fires the animator's end callback synchronously, and that
     * end belongs to a play that no longer runs.
     */
    private fun cancelCurl() {
        playGate.invalidate()
        animator?.cancel()
        animator = null
        fromBitmap = null
        toBitmap = null
    }

    /**
     * Terminal exit for the curl transition. Stops any running animator and
     * hides the view; the aborted play reports no end. Safe to call when no
     * curl is playing.
     */
    fun abortAndHide() {
        cancelCurl()
        isVisible = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val from = fromBitmap ?: return
        val to = toBitmap ?: return
        if (from.width <= 0 || from.height <= 0 || to.width <= 0 || to.height <= 0) {
            return
        }

        PageCurlFrameRenderer.drawFrame(
            canvas,
            from,
            to,
            progress,
            direction,
            verts,
            meshColors,
            shadowPaint,
        )
        drawFoldBack(canvas, from.width.toFloat(), from.height.toFloat())
    }

    /**
     * Draws the softened back of the folded-over sheet inside the projected
     * strip.
     *
     * The back shows the outgoing bitmap mirrored about the crease, washed
     * with a translucent color so it reads as paper rather than as a
     * copy of the front. Outside the strip nothing changes. Both curl
     * directions sample the canonical range [tangent, tangent + radius],
     * inside the bitmap for every non-null span.
     */
    private fun drawFoldBack(canvas: Canvas, bitmapWidth: Float, bitmapHeight: Float) {
        val span = PageCurlRollMath.foldBackSpan(bitmapWidth, progress, direction) ?: return
        // Skip when the whole strip has rolled off screen.
        if (span.endInclusive <= 0f || span.start >= bitmapWidth) return
        val left = span.start
        val right = span.endInclusive

        canvas.save()
        canvas.clipRect(left, 0f, right, bitmapHeight)
        // The right curl reflects the bitmap about the fold line at the
        // strip's outer edge. The mesh already mirrors the page for a left
        // curl, so there the same reflection composes into a pure
        // translation by w - 2t.
        if (direction == CurlDirection.FROM_RIGHT) {
            canvas.scale(-1f, 1f, right, 0f)
        } else {
            val tangent = PageCurlRollMath.tangentX(bitmapWidth, progress)
            canvas.translate(bitmapWidth - 2f * tangent, 0f)
        }
        canvas.drawBitmap(fromBitmap!!, 0f, 0f, null)
        // Soften the sampled copy so it reads as paper.
        canvas.drawRect(left, 0f, right, bitmapHeight, backSoftenPaint)
        canvas.restore()
    }
}

/**
 * Draws one frame of the page curl. The object holds no state; the view owns
 * the mesh buffers and the paint.
 *
 * Draw order: the incoming page first, then the cast shadow beside the fold
 * line on it, then the outgoing sheet as a shaded mesh.
 */
internal object PageCurlFrameRenderer {

    fun drawFrame(
        canvas: Canvas,
        from: Bitmap,
        to: Bitmap,
        progress: Float,
        direction: CurlDirection,
        verts: FloatArray,
        meshColors: IntArray,
        shadowPaint: Paint,
    ) {
        canvas.drawBitmap(to, 0f, 0f, null)
        drawFoldCastShadow(
            canvas,
            from.width.toFloat(),
            from.height.toFloat(),
            progress,
            direction,
            shadowPaint,
        )
        PageCurlRollMath.buildVerts(
            from.width.toFloat(),
            from.height.toFloat(),
            progress,
            direction,
            verts,
        )
        PageCurlRollMath.buildColors(from.width.toFloat(), progress, meshColors)
        canvas.drawBitmapMesh(
            from,
            PageCurlRollMath.MESH_COLS,
            PageCurlRollMath.MESH_ROWS,
            verts,
            0,
            meshColors,
            0,
            null,
        )
    }

    /** Contact shadow on the incoming page beside the fold line. */
    private fun drawFoldCastShadow(
        canvas: Canvas,
        bitmapWidth: Float,
        bitmapHeight: Float,
        progress: Float,
        direction: CurlDirection,
        shadowPaint: Paint,
    ) {
        val alpha = PageCurlRollMath.castShadowAlpha(progress)
        if (alpha <= 0) return

        // Canonical frame: dark at the tangent line, fading right onto the
        // exposed incoming side. Mirror for a left curl.
        val tangent = PageCurlRollMath.tangentX(bitmapWidth, progress)
        val shadowWidth = bitmapWidth * PageCurlRollMath.CAST_SHADOW_WIDTH_FRACTION
        val band = direction.mirrorSpan(tangent..(tangent + shadowWidth), bitmapWidth)
        val startX = band.start
        val endX = band.endInclusive
        shadowPaint.shader = LinearGradient(
            startX,
            0f,
            endX,
            0f,
            intArrayOf(Color.argb(alpha, 0, 0, 0), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(startX, 0f, endX, bitmapHeight, shadowPaint)
        shadowPaint.shader = null
    }
}

/**
 * One-shot validity tokens for curl plays. Each [begin] supersedes every
 * earlier token; [invalidate] supersedes the current token without starting
 * a new play. The overlay runs an animation-end callback only while its
 * token is current, so an aborted curl never reports an end.
 */
internal class CurlPlayGate {
    private var currentToken = 0L

    /** Starts a new play and returns its token. */
    fun begin(): Long {
        currentToken++
        return currentToken
    }

    /** Marks the current token stale. Safe to call with no play running. */
    fun invalidate() {
        currentToken++
    }

    /** True while [token] names the current play. */
    fun isCurrent(token: Long): Boolean = token == currentToken
}
