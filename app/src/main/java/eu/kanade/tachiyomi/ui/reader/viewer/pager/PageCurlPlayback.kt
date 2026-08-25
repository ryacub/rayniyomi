package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd

/**
 * Owns the curl transition playback: the running animator, the displayed
 * bitmap pair, and the play tokens that decide whether an animation end
 * still counts. The overlay view delegates playback here, so the terminality
 * sequencing runs without constructing a View; drawing stays in the view.
 *
 * Invalidation always comes before cancellation: cancelling fires the
 * animator's end callback synchronously, and that end belongs to a play that
 * no longer runs.
 */
internal class PageCurlPlayback(
    private val newAnimator: () -> ValueAnimator = { ValueAnimator.ofFloat(0f, 1f) },
    private val onUpdate: () -> Unit,
) {
    private val playGate = CurlPlayGate()

    var animator: ValueAnimator? = null
        private set
    var fromBitmap: Bitmap? = null
        private set
    var toBitmap: Bitmap? = null
        private set
    var direction: CurlDirection = CurlDirection.FROM_RIGHT
        private set
    var progress: Float = 0f
        private set

    /**
     * Plays the transition from [from] to [to]. Any running play is cancelled
     * first; a cancelled or superseded play reports no end.
     */
    fun play(
        from: Bitmap,
        to: Bitmap,
        direction: CurlDirection,
        durationMs: Long,
        onEnd: () -> Unit,
    ) {
        abort()
        val token = playGate.begin()
        fromBitmap = from
        toBitmap = to
        this.direction = direction
        progress = 0f

        val animator = newAnimator().apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                progress = animation.animatedValue as Float
                onUpdate()
            }
            doOnEnd {
                if (playGate.isCurrent(token)) onEnd()
            }
        }
        this.animator = animator
        animator.start()
    }

    /**
     * Terminal exit for the current play. Invalidates its token before it
     * cancels the animator, so the synchronous end of a cancelled animator
     * never reports.
     */
    fun abort() {
        playGate.invalidate()
        animator?.cancel()
        animator = null
        fromBitmap = null
        toBitmap = null
    }
}
