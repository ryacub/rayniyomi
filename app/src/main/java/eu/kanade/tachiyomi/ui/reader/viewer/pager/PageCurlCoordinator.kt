package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle

/**
 * Owns the page-curl state and resources for one horizontal pager.
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

    private var generationId = 0L
    private var lastNavigationAtMs = 0L

    /**
     * Position the active curl navigated to; null when no curl is tracked.
     */
    private var curlTargetPosition: Int? = null

    private var released = false

    private var targetReadyRunnable: Runnable? = null
    private var layoutCheckRunnable: Runnable? = null
    private var gestureReenableRunnable: Runnable? = null

    private var pendingFromBitmap: Bitmap? = null
    private var activeFromBitmap: Bitmap? = null
    private var activeToBitmap: Bitmap? = null

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
            cancelCurrentCurl(restoreInput = true)
            advance(useAnimation)
            return
        }

        val fromBitmap = capture.capture(source)
        if (fromBitmap == null) {
            cancelCurrentCurl(restoreInput = true)
            advance(useAnimation)
            return
        }
        cancelCurrentCurl()
        pendingFromBitmap = fromBitmap
        // Set after cancelCurrentCurl() so it does not clear the new target, and before
        // advance(false) so the synchronous onPageSelected callback sees a match.
        curlTargetPosition = targetPosition
        advance(false)
        pager.acquireGestures(GestureInputGate.Claim.CURL)
        waitForTarget(targetPosition, targetHolder, fromBitmap, curlFromRight)
    }

    fun release() {
        if (released) return

        released = true
        cancelCurrentCurl()
    }

    /**
     * Notifies the coordinator that the page changed outside [runOrFallback],
     * for example by a swipe. Cancels the active curl when the new position
     * differs from the curl target.
     */
    fun onPageChangedExternally(position: Int) {
        if (released) return
        if (curlTargetPosition != null && position != curlTargetPosition) {
            cancelCurrentCurl(restoreInput = true)
        }
    }

    private fun cancelCurrentCurl(restoreInput: Boolean = false) {
        val hasCurlState = targetReadyRunnable != null ||
            layoutCheckRunnable != null ||
            gestureReenableRunnable != null ||
            pendingFromBitmap != null ||
            activeFromBitmap != null ||
            activeToBitmap != null
        if (!hasCurlState) return

        generationId++
        removeCallbacks()
        curlTargetPosition = null
        val pendingFrom = pendingFromBitmap.also { pendingFromBitmap = null }
        val activeFrom = activeFromBitmap.also { activeFromBitmap = null }
        val activeTo = activeToBitmap.also { activeToBitmap = null }

        overlay.cancelCurl()
        removeCallbacks()
        overlay.isVisible = false
        recycle(pendingFrom)
        recycle(activeFrom)
        recycle(activeTo)
        if (restoreInput) pager.releaseGestures(GestureInputGate.Claim.CURL)
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
            if (released || targetReadyRunnable !== checkTargetReady) return@Runnable

            val readyHolder = readerItemAt(targetPosition)?.let(holderFor) ?: initialTargetHolder
            if (readyHolder != null || waitAttempts >= TARGET_WAIT_MAX_ATTEMPTS) {
                targetReadyRunnable = null
                if (pendingFromBitmap === fromBitmap) pendingFromBitmap = null
                playAndHideCurl(fromBitmap, readyHolder, curlFromRight)
            } else {
                waitAttempts++
                pager.postDelayed(checkTargetReady, TARGET_POLL_INTERVAL_MS)
            }
        }
        targetReadyRunnable = checkTargetReady
        pager.post(checkTargetReady)
    }

    private fun playAndHideCurl(
        fromBitmap: Bitmap,
        targetHolder: PagerPageHolder?,
        curlFromRight: Boolean,
    ) {
        val toBitmap = targetHolder?.let(capture::capture)
        if (toBitmap == null) {
            // This exit bypasses cancelCurrentCurl because no curl state remains.
            curlTargetPosition = null
            recycle(fromBitmap)
            pager.releaseGestures(GestureInputGate.Claim.CURL)
            return
        }

        val animationGenerationId = ++generationId
        activeFromBitmap = fromBitmap
        activeToBitmap = toBitmap
        overlay.playCurl(
            from = fromBitmap,
            to = toBitmap,
            curlFromRight = curlFromRight,
            durationMs = CURL_DURATION_MS,
            onEnd = {
                if (animationGenerationId == generationId) {
                    waitForLayout(fromBitmap, toBitmap)
                } else {
                    recycle(fromBitmap)
                    recycle(toBitmap)
                }
            },
        )
    }

    private fun waitForLayout(fromBitmap: Bitmap, toBitmap: Bitmap) {
        var layoutCheckAttempts = 0
        lateinit var checkLayout: Runnable
        checkLayout = Runnable {
            if (released || layoutCheckRunnable !== checkLayout) return@Runnable

            val currentHolder = readerItemAt(pager.currentItem)?.let(holderFor)
            if (currentHolder?.isLaidOut == true || layoutCheckAttempts >= LAYOUT_WAIT_MAX_ATTEMPTS) {
                layoutCheckRunnable = null
                // Normal completion bypasses cancelCurrentCurl; clear the tracked target here.
                curlTargetPosition = null
                clearActiveBitmaps(fromBitmap, toBitmap)
                overlay.isVisible = false
                overlay.cancelCurl()
                recycle(fromBitmap)
                recycle(toBitmap)

                val reenable = Runnable {
                    if (!released) pager.releaseGestures(GestureInputGate.Claim.CURL)
                }
                gestureReenableRunnable = reenable
                pager.postDelayed(reenable, GESTURE_REENABLE_DELAY_MS)
            } else {
                layoutCheckAttempts++
                pager.postDelayed(checkLayout, TARGET_POLL_INTERVAL_MS)
            }
        }
        layoutCheckRunnable?.let(pager::removeCallbacks)
        layoutCheckRunnable = checkLayout
        pager.post(checkLayout)
    }

    private fun clearActiveBitmaps(fromBitmap: Bitmap, toBitmap: Bitmap) {
        if (activeFromBitmap === fromBitmap && activeToBitmap === toBitmap) {
            activeFromBitmap = null
            activeToBitmap = null
            generationId++
        }
    }

    private fun removeCallbacks() {
        targetReadyRunnable?.let(pager::removeCallbacks)
        targetReadyRunnable = null
        layoutCheckRunnable?.let(pager::removeCallbacks)
        layoutCheckRunnable = null
        gestureReenableRunnable?.let(pager::removeCallbacks)
        gestureReenableRunnable = null
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
