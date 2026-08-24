package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.viewpager.widget.DirectionalViewPager
import eu.kanade.tachiyomi.ui.reader.viewer.GestureDetectorWithLongTap

/**
 * Pager implementation that listens for tap and long tap and allows temporarily disabling touch
 * events in order to work with child views that need to disable touch events on this parent. The
 * pager can also be declared to be vertical by creating it with [isHorizontal] to false.
 */
open class Pager(
    context: Context,
    isHorizontal: Boolean = true,
) : DirectionalViewPager(context, isHorizontal) {

    /**
     * Tap listener function to execute when a tap is detected.
     */
    var tapListener: ((MotionEvent) -> Unit)? = null

    /**
     * Long tap listener function to execute when a long tap is detected.
     */
    var longTapListener: ((MotionEvent) -> Boolean)? = null

    /**
     * Gesture listener that implements tap and long tap events.
     */
    private val gestureListener = object : GestureDetectorWithLongTap.Listener() {
        override fun onSingleTapUp(ev: MotionEvent): Boolean {
            tapListener?.invoke(ev)
            return true
        }

        override fun onLongTapConfirmed(ev: MotionEvent) {
            if (!honorsLongTap(gestureInputMode)) return
            val listener = longTapListener
            if (listener != null && listener.invoke(ev)) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    /**
     * Gesture detector which handles motion events.
     */
    private val gestureDetector = GestureDetectorWithLongTap(
        context,
        gestureListener,
        detectDoubleTap = false,
    )

    /**
     * Arbitrates which gestures the pager accepts. Each owner holds a named claim
     * while it needs a non-default mode. See [GestureInputGate].
     */
    private val gestureGate = GestureInputGate()

    /**
     * The strongest active claim's mode.
     */
    private val gestureInputMode
        get() = gestureGate.effectiveMode

    /**
     * Whether non-navigation gestures (long tap, menu tap) are suppressed.
     */
    internal val isGestureInputSuppressed: Boolean
        get() = gestureInputMode != GestureInputMode.ENABLED

    /**
     * Dispatches a touch event.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        // Taps must reach the listener even while a curl owns the screen. Inside the rapid
        // window the coordinator snaps instead of stacking a curl; outside it a fresh curl
        // replaces the old one. No turn is dropped. Child views that press DISABLED keep the
        // detector silent so their own taps never double-fire pager actions.
        if (feedsTapDetector(gestureInputMode)) {
            gestureDetector.onTouchEvent(ev)
        }
        return handled
    }

    /**
     * Whether the given [ev] should be intercepted. Only used to prevent crashes when child
     * views manipulate [requestDisallowInterceptTouchEvent].
     */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onInterceptTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Handles a touch event. Only used to prevent crashes when child views manipulate
     * [requestDisallowInterceptTouchEvent].
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.onTouchEvent(ev)
        } catch (e: NullPointerException) {
            false
        } catch (e: IndexOutOfBoundsException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Executes the given key event when this pager has focus. Just do nothing because the reader
     * already dispatches key events to the viewer and has more control than this method.
     */
    override fun executeKeyEvent(event: KeyEvent): Boolean {
        // Disable viewpager's default key event handling
        return false
    }

    /**
     * Adds [claim] as an owner of the gesture input mode. Acquiring an active claim
     * changes nothing.
     */
    fun acquireGestures(claim: GestureInputGate.Claim) {
        gestureGate.acquire(claim)
    }

    /**
     * Removes [claim] from the gesture input mode owners. Releasing an inactive
     * claim does nothing. The mode falls back to the strongest remaining claim.
     */
    fun releaseGestures(claim: GestureInputGate.Claim) {
        gestureGate.release(claim)
    }

    enum class GestureInputMode {
        /** All taps, long taps, and menu taps are delivered. */
        ENABLED,

        /** Navigation taps are delivered; long taps and MENU actions are suppressed. */
        SUPPRESS_CHROME,

        /** The gesture detector receives no events at all. */
        DISABLED,
    }

    companion object {
        /**
         * Whether motion events reach the tap gesture detector in [mode]. Actual touch
         * delivery is covered by the device smoke checklist, not a JVM test.
         */
        internal fun feedsTapDetector(mode: GestureInputMode): Boolean =
            mode != GestureInputMode.DISABLED

        /**
         * Whether long taps are honored in [mode].
         */
        internal fun honorsLongTap(mode: GestureInputMode): Boolean =
            mode == GestureInputMode.ENABLED
    }
}
