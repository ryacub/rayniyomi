package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.os.SystemClock
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle

/**
 * Owns the page-curl state and resources for one horizontal pager.
 *
 * All mutable curl-lifecycle state lives in one [CurlState] object that moves
 * through the phases [Phase.IDLE], [Phase.WAITING_FOR_TARGET],
 * [Phase.ANIMATING], and [Phase.WAITING_LAYOUT]. Every terminal exit (fallback
 * cancel, capture failure, superseded curl, external navigation cancel,
 * normal completion, release) routes through [finish], the single teardown
 * point for callbacks, bitmaps, the overlay, and the gesture claim.
 */
internal class PageCurlCoordinator(
    val overlay: PageCurlOverlayView,
    private val pager: Pager,
    private val storedTransitionStyle: () -> PageTransitionStyle,
    private val effectiveTransitionStyle: () -> PageTransitionStyle,
    private val sourceHolder: () -> PagerPageHolder?,
    private val readerItemAt: (Int) -> ReaderPage?,
    private val transitionItemAt: (Int) -> Boolean,
    private val holderFor: (ReaderPage) -> PagerPageHolder?,
    private val nowMs: () -> Long = SystemClock::uptimeMillis,
    private val capture: PageCurlCapture = PageCurlCapture(),
) {

    private var curlState = CurlState()

    /**
     * Navigation-cadence timestamp for the rapid-navigation window. It tracks
     * navigation rate, not one curl, so it survives across curls while
     * [CurlState] returns to [Phase.IDLE].
     */
    private var lastNavigationAtMs = 0L

    /** Coordinator-lifetime flag; outlives any single [CurlState] cycle. */
    private var released = false

    private enum class Phase {
        /** No curl tracked. */
        IDLE,

        /** Source captured; polling for the target holder. */
        WAITING_FOR_TARGET,

        /** [PageCurlOverlayView.playCurl] running. */
        ANIMATING,

        /** Animation finished; polling for target layout. */
        WAITING_LAYOUT,
    }

    /**
     * One curl's tracked runnables and bitmaps. Main-thread only: ViewPager
     * callbacks, posted runnables, and the view animator all run there, so
     * the fields need no synchronization.
     */
    private class CurlState {
        var phase = Phase.IDLE
        var generationId = 0L
        var targetPosition: Int? = null
        var targetReadyRunnable: Runnable? = null
        var layoutCheckRunnable: Runnable? = null
        var gestureReenableRunnable: Runnable? = null
        var pendingFromBitmap: Bitmap? = null
        var activeFromBitmap: Bitmap? = null
        var activeToBitmap: Bitmap? = null

        /**
         * True when no tracked resource remains. The teardown guard uses only
         * this predicate, never [phase]: no invariant enforces that a
         * non-[Phase.IDLE] phase holds resources, and a phase-based guard
         * could skip teardown and leak bitmaps or the gesture claim.
         */
        fun isEmpty(): Boolean =
            targetReadyRunnable == null &&
                layoutCheckRunnable == null &&
                gestureReenableRunnable == null &&
                pendingFromBitmap == null &&
                activeFromBitmap == null &&
                activeToBitmap == null

        fun takeBitmaps(): Triple<Bitmap?, Bitmap?, Bitmap?> {
            val pendingFrom = pendingFromBitmap.also { pendingFromBitmap = null }
            val activeFrom = activeFromBitmap.also { activeFromBitmap = null }
            val activeTo = activeToBitmap.also { activeToBitmap = null }
            return Triple(pendingFrom, activeFrom, activeTo)
        }

        fun clearRunnables(pager: Pager) {
            targetReadyRunnable?.let(pager::removeCallbacks)
            targetReadyRunnable = null
            layoutCheckRunnable?.let(pager::removeCallbacks)
            layoutCheckRunnable = null
            gestureReenableRunnable?.let(pager::removeCallbacks)
            gestureReenableRunnable = null
        }
    }

    fun runOrFallback(
        targetPosition: Int,
        curlFromRight: Boolean,
        advance: (animate: Boolean) -> Unit,
    ) {
        if (released) return

        val now = nowMs()
        val preference = storedTransitionStyle()
        val withinRapidNavigationWindow = now - lastNavigationAtMs < RAPID_NAVIGATION_WINDOW_MS
        lastNavigationAtMs = now

        val style = if (preference == PageTransitionStyle.CURL) {
            effectiveTransitionStyle()
        } else {
            preference
        }
        val useAnimation = style != PageTransitionStyle.NONE
        val source = sourceHolder()
        val targetHolder = readerItemAt(targetPosition)?.let(holderFor)

        val canAttemptCurl = source != null &&
            shouldAttemptCurl(
                style = style,
                targetIsChapterTransition = transitionItemAt(targetPosition),
                sourceAtMinimumZoom = source.isAtMinimumZoom(),
                targetAtMinimumZoom = targetHolder?.isAtMinimumZoom() ?: true,
                withinRapidNavigationWindow = withinRapidNavigationWindow,
            )

        if (!canAttemptCurl) {
            finish(restoreInput = true)
            advance(useAnimation)
            return
        }

        // Capture the source before finish() cancels the previous curl, so
        // the previous overlay frame is still intact during the capture.
        val fromBitmap = capture.capture(source)
        if (fromBitmap == null) {
            finish(restoreInput = true)
            advance(useAnimation)
            return
        }
        // No input restore here: the new acquire below supersedes the claim.
        finish(restoreInput = false)
        curlState.pendingFromBitmap = fromBitmap
        // Set after finish() so it does not clear the new target, and before
        // advance(false) so the synchronous onPageSelected callback sees a match.
        curlState.targetPosition = targetPosition
        advance(false)
        pager.acquireGestures(GestureInputGate.Claim.CURL)
        curlState.phase = Phase.WAITING_FOR_TARGET
        waitForTarget(targetPosition, targetHolder, fromBitmap, curlFromRight)
    }

    fun release() {
        if (released) return

        released = true
        finish(restoreInput = false)
    }

    /**
     * Notifies the coordinator that the page changed outside [runOrFallback],
     * for example by a swipe. Cancels the active curl when the new position
     * differs from the curl target.
     */
    fun onPageChangedExternally(position: Int) {
        if (released) return
        if (curlState.targetPosition != null && position != curlState.targetPosition) {
            finish(restoreInput = true)
        }
    }

    /**
     * The single terminal exit. Tears down every tracked resource exactly
     * once and returns [CurlState] to [Phase.IDLE].
     *
     * The generation bump precedes [PageCurlOverlayView.cancelCurl]: that
     * order makes a reentrant animation-end callback (via animator cancel)
     * take the stale branch instead of tearing down the animating pair twice.
     */
    private fun finish(restoreInput: Boolean) {
        val state = curlState
        if (state.isEmpty()) return

        state.generationId++
        state.clearRunnables(pager)
        state.targetPosition = null
        val (pendingFrom, activeFrom, activeTo) = state.takeBitmaps()
        overlay.abortAndHide()
        recycle(pendingFrom)
        recycle(activeFrom)
        recycle(activeTo)
        if (restoreInput) pager.releaseGestures(GestureInputGate.Claim.CURL)
        state.phase = Phase.IDLE
    }

    private fun waitForTarget(
        targetPosition: Int,
        initialTargetHolder: PagerPageHolder?,
        fromBitmap: Bitmap,
        curlFromRight: Boolean,
    ) {
        var waitAttempts = 0
        lateinit var checkTargetReady: Runnable
        checkTargetReady = Runnable {
            if (released || curlState.targetReadyRunnable !== checkTargetReady) return@Runnable

            val readyHolder = readerItemAt(targetPosition)?.let(holderFor) ?: initialTargetHolder
            if (readyHolder != null || waitAttempts >= TARGET_WAIT_MAX_ATTEMPTS) {
                curlState.targetReadyRunnable = null
                if (curlState.pendingFromBitmap === fromBitmap) curlState.pendingFromBitmap = null
                playAndHideCurl(fromBitmap, readyHolder, curlFromRight)
            } else {
                waitAttempts++
                pager.postDelayed(checkTargetReady, TARGET_POLL_INTERVAL_MS)
            }
        }
        curlState.targetReadyRunnable = checkTargetReady
        pager.post(checkTargetReady)
    }

    private fun playAndHideCurl(
        fromBitmap: Bitmap,
        targetHolder: PagerPageHolder?,
        curlFromRight: Boolean,
    ) {
        // Store before the target capture so a capture failure tears down
        // through finish() like every other exit.
        curlState.activeFromBitmap = fromBitmap
        val toBitmap = targetHolder?.let(capture::capture)
        if (toBitmap == null) {
            finish(restoreInput = true)
            return
        }

        curlState.phase = Phase.ANIMATING
        val animationGenerationId = ++curlState.generationId
        curlState.activeToBitmap = toBitmap
        overlay.playCurl(
            from = fromBitmap,
            to = toBitmap,
            curlFromRight = curlFromRight,
            durationMs = CURL_DURATION_MS,
            onEnd = {
                if (animationGenerationId == curlState.generationId) {
                    waitForLayout(fromBitmap, toBitmap)
                } else {
                    recycle(fromBitmap)
                    recycle(toBitmap)
                }
            },
        )
    }

    private fun waitForLayout(fromBitmap: Bitmap, toBitmap: Bitmap) {
        curlState.phase = Phase.WAITING_LAYOUT
        var layoutCheckAttempts = 0
        lateinit var checkLayout: Runnable
        checkLayout = Runnable {
            if (released || curlState.layoutCheckRunnable !== checkLayout) return@Runnable

            val currentHolder = readerItemAt(pager.currentItem)?.let(holderFor)
            if (currentHolder?.isLaidOut == true || layoutCheckAttempts >= LAYOUT_WAIT_MAX_ATTEMPTS) {
                finish(restoreInput = false)

                // Hold the claim briefly after the animation ends so a tap
                // landing on the finished frame does not trigger a turn.
                val reenable = Runnable {
                    if (!released) pager.releaseGestures(GestureInputGate.Claim.CURL)
                }
                curlState.gestureReenableRunnable = reenable
                pager.postDelayed(reenable, GESTURE_REENABLE_DELAY_MS)
            } else {
                layoutCheckAttempts++
                pager.postDelayed(checkLayout, TARGET_POLL_INTERVAL_MS)
            }
        }
        curlState.layoutCheckRunnable?.let(pager::removeCallbacks)
        curlState.layoutCheckRunnable = checkLayout
        pager.post(checkLayout)
    }

    private fun recycle(bitmap: Bitmap?) {
        if (bitmap != null && !bitmap.isRecycled) bitmap.recycle()
    }

    companion object {
        private const val CURL_DURATION_MS = 300L
        private const val RAPID_NAVIGATION_WINDOW_MS = 500L
        private const val TARGET_POLL_INTERVAL_MS = 10L
        private const val TARGET_WAIT_MAX_ATTEMPTS = 10
        private const val LAYOUT_WAIT_MAX_ATTEMPTS = 50
        private const val GESTURE_REENABLE_DELAY_MS = 50L

        fun shouldAttemptCurl(
            style: PageTransitionStyle,
            targetIsChapterTransition: Boolean,
            sourceAtMinimumZoom: Boolean,
            targetAtMinimumZoom: Boolean,
            withinRapidNavigationWindow: Boolean,
        ): Boolean {
            if (style != PageTransitionStyle.CURL) return false
            if (targetIsChapterTransition) return false
            if (!sourceAtMinimumZoom || !targetAtMinimumZoom) return false
            if (withinRapidNavigationWindow) return false
            return true
        }
    }
}
