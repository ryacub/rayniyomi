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
                intArrayOf(Color.argb(alpha, 0, 0, 0), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(startX, 0f, endX, bitmapHeight, shadowPaint)
            shadowPaint.shader = null
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
        isVisible = true
        playback.play(from, to, direction, durationMs, onEnd)
    }

    /**
     * Terminal exit for the curl transition. Stops the running play and
     * hides the view; the aborted play reports no end. Safe to call when no
     * curl is playing.
     */
    fun abortAndHide() {
        playback.abort()
        isVisible = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val from = playback.fromBitmap ?: return
        val to = playback.toBitmap ?: return
        if (from.width <= 0 || from.height <= 0 || to.width <= 0 || to.height <= 0) {
            return
        }
        drawFrame(
            canvas,
            from,
            to,
            playback.progress,
            playback.direction,
            verts,
            meshColors,
            shadowPaint,
        )
        PageCurlBackFaceRenderer.draw(
            canvas,
            from,
            playback.progress,
            playback.direction,
            backSoftenPaint,
        )
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
