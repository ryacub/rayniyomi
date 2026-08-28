package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PageCurlCapture.CapturedPage

/**
 * Draws the page curl transition on top of the pager.
 *
 * The view draws nothing until [playCurl] runs. The caller starts
 * transitions with [playCurl] and ends them with [abortAndHide].
 */
class PageCurlOverlayView(context: Context) : View(context) {

    companion object {
        // Near-white wash over the mirrored strip so the back reads as paper
        // rather than as a mirrored copy of the front.
        private val BACK_SOFTEN_COLOR = Color.argb(90, 255, 255, 255)

        /**
         * Draws one frame of the page curl. Stateless, so tests and callers
         * can run it without constructing a View. The caller owns the mesh
         * buffers and the paint.
         *
         * Draw order: the incoming page first, then the cast shadow beside
         * the fold line on it, then the outgoing sheet as a shaded mesh.
         */
        internal fun drawFrame(
            canvas: Canvas,
            from: Bitmap,
            to: Bitmap,
            progress: Float,
            direction: CurlDirection,
            verts: FloatArray,
            meshColors: IntArray,
            shadowPaint: Paint,
        ) {
            drawFrame(
                canvas = canvas,
                from = from,
                to = to,
                fromBounds = bitmapBounds(from),
                toBounds = bitmapBounds(to),
                fromOrigin = PointF(),
                toOrigin = PointF(),
                progress = progress,
                direction = direction,
                verts = verts,
                meshColors = meshColors,
                shadowPaint = shadowPaint,
            )
        }

        internal fun drawFrame(
            canvas: Canvas,
            from: Bitmap,
            to: Bitmap,
            fromBounds: RectF,
            toBounds: RectF,
            progress: Float,
            direction: CurlDirection,
            verts: FloatArray,
            meshColors: IntArray,
            shadowPaint: Paint,
        ) {
            drawFrame(
                canvas,
                from,
                to,
                fromBounds,
                toBounds,
                point(fromBounds.left, fromBounds.top),
                point(toBounds.left, toBounds.top),
                progress,
                direction,
                verts,
                meshColors,
                shadowPaint,
            )
        }

        internal fun drawFrame(
            canvas: Canvas,
            from: Bitmap,
            to: Bitmap,
            fromBounds: RectF,
            toBounds: RectF,
            fromOrigin: PointF,
            toOrigin: PointF,
            progress: Float,
            direction: CurlDirection,
            verts: FloatArray,
            meshColors: IntArray,
            shadowPaint: Paint,
        ) {
            canvas.save()
            canvas.clipRect(toBounds)
            canvas.drawBitmap(to, toOrigin.x, toOrigin.y, null)
            canvas.restore()
            canvas.save()
            canvas.clipRect(fromBounds)
            canvas.translate(fromOrigin.x, fromOrigin.y)
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
            PageCurlRollMath.buildColors(
                from.width.toFloat(),
                progress,
                direction,
                meshColors,
            )
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
            canvas.restore()
        }

        internal fun drawBackFace(
            canvas: Canvas,
            bitmap: Bitmap,
            bounds: RectF,
            progress: Float,
            direction: CurlDirection,
            softenPaint: Paint,
        ) {
            drawBackFace(
                canvas,
                bitmap,
                bounds,
                point(bounds.left, bounds.top),
                progress,
                direction,
                softenPaint,
            )
        }

        internal fun drawBackFace(
            canvas: Canvas,
            bitmap: Bitmap,
            bounds: RectF,
            origin: PointF,
            progress: Float,
            direction: CurlDirection,
            softenPaint: Paint,
        ) {
            canvas.save()
            canvas.clipRect(bounds)
            canvas.translate(origin.x, origin.y)
            PageCurlBackFaceRenderer.draw(canvas, bitmap, progress, direction, softenPaint)
            canvas.restore()
        }

        private fun bitmapBounds(bitmap: Bitmap): RectF = RectF().apply {
            right = bitmap.width.toFloat()
            bottom = bitmap.height.toFloat()
        }

        private fun point(x: Float, y: Float): PointF = PointF().apply {
            this.x = x
            this.y = y
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

            // Canonical frame: dark at the tangent line, fading right onto
            // the exposed incoming side. Mirror for a left curl.
            val tangent = PageCurlRollMath.tangentX(bitmapWidth, progress)
            val shadowWidth = bitmapWidth * PageCurlRollMath.CAST_SHADOW_WIDTH_FRACTION
            val band = direction.mirrorSpan(
                tangent..(tangent + shadowWidth),
                bitmapWidth,
            )
            val startX = band.start
            val endX = band.endInclusive

            shadowPaint.shader = LinearGradient(
                startX,
                0f,
                endX,
                0f,
                castShadowColors(direction, alpha),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(startX, 0f, endX, bitmapHeight, shadowPaint)
            shadowPaint.shader = null
        }

        internal fun castShadowColors(direction: CurlDirection, alpha: Int): IntArray {
            val dark = Color.argb(alpha, 0, 0, 0)
            return if (direction == CurlDirection.FROM_LEFT) {
                intArrayOf(Color.TRANSPARENT, dark)
            } else {
                intArrayOf(dark, Color.TRANSPARENT)
            }
        }
    }

    private val playback = PageCurlPlayback(
        newAnimator = { ValueAnimator.ofFloat(0f, 1f) },
        onUpdate = { invalidate() },
    )

    private val verts = FloatArray(PageCurlRollMath.vertCount())
    private val meshColors = PageCurlRollMath.newColors()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backSoftenPaint = Paint().apply { color = BACK_SOFTEN_COLOR }
    private var fromBounds: RectF? = null
    private var toBounds: RectF? = null
    private var fromOrigin: PointF? = null
    private var toOrigin: PointF? = null

    init {
        isVisible = false
    }

    /**
     * Plays the curl transition from [from] to [to].
     *
     * The animator invokes [onEnd] exactly once when it finishes. An abort
     * or a newer play supersedes this play, and [onEnd] never runs.
     */
    internal fun playCurl(
        from: CapturedPage,
        to: CapturedPage,
        direction: CurlDirection,
        durationMs: Long,
        onEnd: () -> Unit,
    ) {
        fromBounds = from.bounds.copy()
        toBounds = to.bounds.copy()
        fromOrigin = from.origin
        toOrigin = to.origin
        isVisible = true
        playback.play(from.bitmap, to.bitmap, direction, durationMs, onEnd)
    }

    /**
     * Terminal exit for the curl transition. Stops the running play and
     * hides the view; the aborted play reports no end. Safe to call when no
     * curl is playing.
     */
    fun abortAndHide() {
        playback.abort()
        fromBounds = null
        toBounds = null
        fromOrigin = null
        toOrigin = null
        isVisible = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val from = playback.fromBitmap ?: return
        val to = playback.toBitmap ?: return
        val fromBounds = fromBounds ?: return
        val toBounds = toBounds ?: return
        val fromOrigin = fromOrigin ?: return
        val toOrigin = toOrigin ?: return
        if (from.width <= 0 || from.height <= 0 || to.width <= 0 || to.height <= 0) {
            return
        }
        drawFrame(
            canvas,
            from,
            to,
            fromBounds,
            toBounds,
            fromOrigin,
            toOrigin,
            playback.progress,
            playback.direction,
            verts,
            meshColors,
            shadowPaint,
        )
        drawBackFace(
            canvas,
            from,
            fromBounds,
            fromOrigin,
            playback.progress,
            playback.direction,
            backSoftenPaint,
        )
    }
}

private fun RectF.copy(): RectF = RectF().apply {
    left = this@copy.left
    top = this@copy.top
    right = this@copy.right
    bottom = this@copy.bottom
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
