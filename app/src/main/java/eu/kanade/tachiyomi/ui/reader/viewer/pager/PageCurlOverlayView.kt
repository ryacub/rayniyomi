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
import kotlin.math.PI
import kotlin.math.sin

/**
 * Draws the page curl transition on top of the pager.
 *
 * The view draws nothing until [playCurl] runs. The caller decides when it is
 * safe to start the transition and when it is safe to hide the view.
 */
class PageCurlOverlayView(context: Context) : View(context) {

    private companion object {
        private const val MESH_COLS = 10
        private const val MESH_ROWS = 10

        // The fraction of the page that stays flat behind the folding edge.
        private const val LAG = 0.5f

        // The lift of the folding edge as a fraction of the page height.
        private const val LIFT_FRACTION = 0.04f

        // The width of the shadow band as a fraction of the page width.
        private const val SHADOW_WIDTH_FRACTION = 0.08f

        private val SHADOW_DARK = Color.argb(51, 0, 0, 0)
    }

    private var animator: ValueAnimator? = null
    private var fromBitmap: Bitmap? = null
    private var toBitmap: Bitmap? = null
    private var curlFromRight = true
    private var progress = 0f

    private val verts = FloatArray((MESH_COLS + 1) * (MESH_ROWS + 1) * 2)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isVisible = false
    }

    /**
     * Plays the curl transition from [from] to [to].
     *
     * The animator invokes [onEnd] exactly once when it finishes or when it is
     * cancelled.
     */
    fun playCurl(
        from: Bitmap,
        to: Bitmap,
        curlFromRight: Boolean,
        durationMs: Long = 300L,
        onEnd: () -> Unit,
    ) {
        cancelCurl()
        fromBitmap = from
        toBitmap = to
        this.curlFromRight = curlFromRight
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
                onEnd()
            }
        }
        this.animator = animator
        animator.start()
    }

    /**
     * Stops the running curl animator, if any.
     */
    fun cancelCurl() {
        animator?.cancel()
        animator = null
        fromBitmap = null
        toBitmap = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val from = fromBitmap ?: return
        val to = toBitmap ?: return
        if (from.width <= 0 || from.height <= 0 || to.width <= 0 || to.height <= 0) {
            return
        }

        // The incoming page sits underneath; it becomes visible when the
        // outgoing page folds away.
        canvas.drawBitmap(to, 0f, 0f, null)

        buildCurlMesh(from.width.toFloat(), from.height.toFloat())
        canvas.drawBitmapMesh(from, MESH_COLS, MESH_ROWS, verts, 0, null, 0, null)

        drawFoldShadow(canvas, from.width.toFloat(), from.height.toFloat())
    }

    /**
     * Warps the mesh grid so the curling edge lifts and folds toward the
     * center as [progress] advances from 0 to 1.
     *
     * The curling edge is the right edge when [curlFromRight] is true and the
     * left edge otherwise.
     */
    private fun buildCurlMesh(bitmapWidth: Float, bitmapHeight: Float) {
        val direction = if (curlFromRight) -1f else 1f
        var index = 0
        for (row in 0..MESH_ROWS) {
            for (col in 0..MESH_COLS) {
                val xFraction = col.toFloat() / MESH_COLS

                // The distance from the curling edge, 0 at the edge.
                val distance = if (curlFromRight) 1f - xFraction else xFraction

                // Progress lags behind by LAG per unit of distance, so the edge
                // advances before the far side starts to move. The denominator
                // is at least 0.5, so it never divides by zero.
                val localProgress =
                    ((progress - distance * LAG) / (1f - distance * LAG)).coerceIn(0f, 1f)

                verts[index] =
                    bitmapWidth * xFraction + bitmapWidth * localProgress * direction
                verts[index + 1] =
                    bitmapHeight * row / MESH_ROWS -
                    bitmapHeight * LIFT_FRACTION * sin(localProgress * PI).toFloat()
                index += 2
            }
        }
    }

    /**
     * Draws a shadow band along the fold line.
     *
     * The shadow is darkest at the fold and fades away from it.
     */
    private fun drawFoldShadow(canvas: Canvas, bitmapWidth: Float, bitmapHeight: Float) {
        // The fold reaches the far edge when the progress reaches LAG.
        val travel = (progress / LAG).coerceIn(0f, 1f)
        val foldX = if (curlFromRight) bitmapWidth * (1f - travel) else bitmapWidth * travel
        val shadowWidth = bitmapWidth * SHADOW_WIDTH_FRACTION

        // The shadow falls on the flat part of the outgoing page.
        val (startX, endX) = if (curlFromRight) {
            (foldX - shadowWidth) to foldX
        } else {
            foldX to (foldX + shadowWidth)
        }
        val colors = if (curlFromRight) {
            intArrayOf(Color.TRANSPARENT, SHADOW_DARK)
        } else {
            intArrayOf(SHADOW_DARK, Color.TRANSPARENT)
        }

        shadowPaint.shader = LinearGradient(
            startX,
            0f,
            endX,
            0f,
            colors,
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(startX, 0f, endX, bitmapHeight, shadowPaint)
        shadowPaint.shader = null
    }
}
