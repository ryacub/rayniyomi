package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.os.SystemClock
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PageCurlCapture.CapturedPage

/**
 * Owns the page-curl state and resources for one horizontal pager.
 *
 * All mutable curl-lifecycle state lives in one [CurlState] object that tracks
 * its own resources: pending and active bitmaps, plus the runnables for the
 * target poll, the layout poll, and the gesture reenable. Every terminal exit
 * (fallback cancel, capture failure, superseded curl, external navigation
 * cancel, normal completion, release) routes through [finish], the single
 * teardown point for callbacks, bitmaps, the overlay, and the gesture claim.
 */
internal class PageCurlCoordinator(
    val overlay: PageCurlOverlayView,
    private val pager: Pager,
    private val scheduler: PageCurlScheduler = object : PageCurlScheduler {
        override fun post(runnable: Runnable): Boolean = pager.post(runnable)

        override fun postDelayed(runnable: Runnable, delayMs: Long): Boolean =
            pager.postDelayed(runnable, delayMs)

        override fun removeCallbacks(runnable: Runnable) {
            pager.removeCallbacks(runnable)
        }
    },
    private val storedTransitionStyle: () -> PageTransitionStyle,
    private val effectiveTransitionStyle: () -> PageTransitionStyle,
    private val sourceHolder: () -> PagerPageHolder?,
    private val readerItemAt: (Int) -> ReaderPage?,
    private val transitionItemAt: (Int) -> Boolean,
    private val holderFor: (ReaderPage) -> PagerPageHolder?,
    private val nowMs: () -> Long = SystemClock::uptimeMillis,
    private val capture: PageCurlCapture = PageCurlCapture(),
) {

    private data class QueuedTurn(
        val targetPosition: Int,
        val direction: CurlDirection,
        val advance: (animate: Boolean) -> Unit,
    )

    private data class PendingFallback(
        val turn: QueuedTurn,
        val waitForIdle: Boolean,
        var selected: Boolean = false,
    )

    private var curlState = CurlState()
    private val queuedTurns = ArrayDeque<QueuedTurn>()
    private var activeTurn: QueuedTurn? = null
    private var pendingFallback: PendingFallback? = null

    /**
     * Navigation-cadence timestamp for the rapid-navigation window. It tracks
     * navigation rate, not one curl, so it survives while [CurlState] resets
     * between curls.
     */
    private var lastNavigationAtMs = 0L

    /** Coordinator-lifetime flag; outlives any single [CurlState] cycle. */
    private var released = false

    fun runOrFallback(
        targetPosition: Int,
        direction: CurlDirection,
        advance: (animate: Boolean) -> Unit,
    ) {
        if (released) return

        val now = nowMs()
        val withinRapidNavigationWindow = now - lastNavigationAtMs < RAPID_NAVIGATION_WINDOW_MS
        lastNavigationAtMs = now

        val turn = QueuedTurn(targetPosition, direction, advance)
        if (activeTurn != null || pendingFallback != null) {
            queuedTurns.addLast(turn)
            return
        }

        startTurn(turn, withinRapidNavigationWindow)
    }

    /** Starts one turn or hands it to the existing non-curl fallback path. */
    private fun startTurn(turn: QueuedTurn, withinRapidNavigationWindow: Boolean) {
        val preference = storedTransitionStyle()
        val style = if (preference == PageTransitionStyle.CURL) {
            effectiveTransitionStyle()
        } else {
            preference
        }
        val useAnimation = style != PageTransitionStyle.NONE
        val source = sourceHolder()
        val targetHolder = readerItemAt(turn.targetPosition)?.let(holderFor)

        val canAttemptCurl = source != null &&
            shouldAttemptCurl(
                style = style,
                targetIsChapterTransition = transitionItemAt(turn.targetPosition),
                sourceAtMinimumZoom = source.isAtMinimumZoom(),
                targetAtMinimumZoom = targetHolder?.isAtMinimumZoom() ?: true,
                withinRapidNavigationWindow = withinRapidNavigationWindow,
            )

        if (!canAttemptCurl) {
            startFallback(turn, useAnimation)
            return
        }

        // Capture the source before finish() cancels the previous curl, so
        // the previous overlay frame is still intact during the capture.
        val fromPage = capture.capture(source)
        if (fromPage == null) {
            startFallback(turn, useAnimation)
            return
        }
        // No input restore here: the new acquire below supersedes the claim.
        finish(restoreInput = false)
        activeTurn = turn
        curlState.pendingFromPage = fromPage
        // Set after finish() so it does not clear the new target, and before
        // advance(false) so the synchronous onPageSelected callback sees a match.
        curlState.targetPosition = turn.targetPosition
        turn.advance(false)
        pager.acquireGestures(GestureInputGate.Claim.CURL)
        waitForTarget(turn.targetPosition, targetHolder, fromPage, turn.direction)
    }

    fun release() {
        if (released) return

        released = true
        queuedTurns.clear()
        pendingFallback = null
        finish(restoreInput = true)
    }

    /** Continues a queued turn after a fallback animation reaches its idle state. */
    fun onPagerIdle() {
        val fallback = pendingFallback ?: return
        if (fallback.selected) continueAfterFallback()
    }

    /**
     * Notifies the coordinator that the page changed outside [runOrFallback],
     * for example by a swipe. Cancels the active curl when the new position
     * differs from the curl target.
     */
    fun onPageChangedExternally(position: Int) {
        if (released) return

        pendingFallback?.let { fallback ->
            if (position == fallback.turn.targetPosition) {
                fallback.selected = true
                if (!fallback.waitForIdle) continueAfterFallback()
                return
            } else {
                pendingFallback = null
                queuedTurns.clear()
            }
        }

        if (activeTurn != null && position != curlState.targetPosition) {
            queuedTurns.clear()
            finish(restoreInput = true)
        }
    }

    /**
     * The single terminal exit. Tears down every tracked resource exactly
     * once and empties [CurlState].
     */
    private fun finish(restoreInput: Boolean) {
        val state = curlState
        activeTurn = null
        if (state.isEmpty()) {
            if (restoreInput) {
                pager.releaseGestures(GestureInputGate.Claim.CURL)
            }
            return
        }

        state.finish(scheduler) { overlay.abortAndHide() }
        if (restoreInput) pager.releaseGestures(GestureInputGate.Claim.CURL)
    }

    private fun startFallback(turn: QueuedTurn, useAnimation: Boolean) {
        finish(restoreInput = true)
        val fallback = PendingFallback(turn = turn, waitForIdle = useAnimation)
        pendingFallback = fallback
        turn.advance(useAnimation)

        if (pendingFallback === fallback && pager.currentItem == turn.targetPosition) {
            fallback.selected = true
            if (!fallback.waitForIdle) continueAfterFallback()
        }
    }

    private fun continueAfterFallback() {
        pendingFallback = null
        startNextQueuedTurn()
    }

    private fun startNextQueuedTurn() {
        if (queuedTurns.isEmpty()) return
        val next = queuedTurns.removeFirst()
        startTurn(next, withinRapidNavigationWindow = false)
    }

    private fun continueAfterCurl() {
        if (queuedTurns.isNotEmpty()) {
            startNextQueuedTurn()
            return
        }

        // Hold the claim briefly after the animation ends so a tap landing on
        // the finished frame does not trigger a turn.
        val reenable = Runnable {
            curlState.fire(TrackedRole.GESTURE_REENABLE)
            if (!released) pager.releaseGestures(GestureInputGate.Claim.CURL)
        }
        scheduler.postDelayed(
            curlState.track(TrackedRole.GESTURE_REENABLE, reenable),
            GESTURE_REENABLE_DELAY_MS,
        )
    }

    private fun waitForTarget(
        targetPosition: Int,
        initialTargetHolder: PagerPageHolder?,
        fromPage: CapturedPage,
        direction: CurlDirection,
    ) {
        var waitAttempts = 0
        lateinit var checkTargetReady: Runnable
        checkTargetReady = Runnable {
            if (released || !curlState.isCurrent(TrackedRole.TARGET_POLL, checkTargetReady)) {
                return@Runnable
            }

            val readyHolder = readerItemAt(targetPosition)?.let(holderFor) ?: initialTargetHolder
            if (readyHolder != null || waitAttempts >= TARGET_WAIT_MAX_ATTEMPTS) {
                curlState.fire(TrackedRole.TARGET_POLL)
                if (curlState.pendingFromPage === fromPage) curlState.pendingFromPage = null
                playAndHideCurl(fromPage, readyHolder, direction)
            } else {
                waitAttempts++
                scheduler.postDelayed(checkTargetReady, TARGET_POLL_INTERVAL_MS)
            }
        }
        scheduler.post(curlState.track(TrackedRole.TARGET_POLL, checkTargetReady))
    }

    private fun playAndHideCurl(
        fromPage: CapturedPage,
        targetHolder: PagerPageHolder?,
        direction: CurlDirection,
    ) {
        // Store before the target capture so a capture failure tears down
        // through finish() like every other exit.
        curlState.activeFromPage = fromPage
        val toPage = targetHolder?.let(capture::capture)
        if (toPage == null) {
            finish(restoreInput = true)
            startNextQueuedTurn()
            return
        }

        curlState.activeToPage = toPage
        overlay.playCurl(
            from = fromPage,
            to = toPage,
            direction = direction,
            durationMs = CURL_DURATION_MS,
            onEnd = {
                // The overlay owns terminality: onEnd runs only while this
                // play is current. Aborted plays report no end, and their
                // pages close through finish().
                waitForLayout(fromPage, toPage)
            },
        )
    }

    private fun waitForLayout(fromPage: CapturedPage, toPage: CapturedPage) {
        var layoutCheckAttempts = 0
        lateinit var checkLayout: Runnable
        checkLayout = Runnable {
            if (released || !curlState.isCurrent(TrackedRole.LAYOUT_POLL, checkLayout)) {
                return@Runnable
            }

            val currentHolder = readerItemAt(pager.currentItem)?.let(holderFor)
            if (currentHolder?.isLaidOut == true || layoutCheckAttempts >= LAYOUT_WAIT_MAX_ATTEMPTS) {
                finish(restoreInput = false)
                continueAfterCurl()
            } else {
                layoutCheckAttempts++
                scheduler.postDelayed(checkLayout, TARGET_POLL_INTERVAL_MS)
            }
        }
        scheduler.post(curlState.track(TrackedRole.LAYOUT_POLL, checkLayout))
    }

    companion object {
        private const val CURL_DURATION_MS = 500L
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
