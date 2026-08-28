package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PageCurlCapture.CapturedPage
import io.mockk.every
import io.mockk.mockk

/**
 * Shared test fixture for [PageCurlCoordinator]. It records posted and delayed
 * callbacks, tracks the current item and gesture input state, and exposes a
 * mutable clock.
 */
internal class PageCurlCoordinatorFixture(
    pageCount: Int = DEFAULT_PAGE_COUNT,
    initialItemIndex: Int = TARGET_POSITION,
) {
    val pager = mockk<Pager>(relaxed = true)
    val overlay = mockk<PageCurlOverlayView>(relaxed = true)
    val capture = mockk<PageCurlCapture>(relaxed = true)
    val sourceHolder = mockk<PagerPageHolder>(relaxed = true)
    val targetHolder = mockk<PagerPageHolder>(relaxed = true)
    private val pages = List(pageCount) { ReaderPage(it) }
    val endCallbacks = mutableListOf<() -> Unit>()
    val playedDirections = mutableListOf<CurlDirection>()
    val advancedAnimations = mutableListOf<Boolean>()
    val delayedCallbacks = mutableListOf<Runnable>()
    private val postedCallbacks = ArrayDeque<Runnable>()
    var nowMs = 1_000L
    var currentItemIndex = initialItemIndex

    /**
     * Emulates the overlay contract: an abort invalidates the play token,
     * so a recorded end callback runs only while its play is current.
     */
    val playGate = CurlPlayGate()

    /**
     * Deterministic fake driving the coordinator's scheduler seam through
     * the recorded pager queues.
     */
    val scheduler = object : PageCurlScheduler {
        override fun post(runnable: Runnable): Boolean = pager.post(runnable)

        override fun postDelayed(runnable: Runnable, delayMs: Long): Boolean =
            pager.postDelayed(runnable, delayMs)

        override fun removeCallbacks(runnable: Runnable) {
            pager.removeCallbacks(runnable)
        }
    }
    val gestureGate = GestureInputGate()

    /** Count of not-yet-run posted callbacks. */
    val pendingPostedCount: Int
        get() = postedCallbacks.size

    /**
     * Mirrors [Pager.gestureInputMode]: true only when no claim constrains gestures.
     */
    val inputEnabled: Boolean
        get() = gestureGate.effectiveMode == Pager.GestureInputMode.ENABLED

    val coordinator = PageCurlCoordinator(
        overlay = overlay,
        pager = pager,
        scheduler = scheduler,
        storedTransitionStyle = { PageTransitionStyle.CURL },
        effectiveTransitionStyle = { PageTransitionStyle.CURL },
        sourceHolder = { sourceHolder },
        readerItemAt = { pages.getOrNull(it) },
        capture = capture,
        transitionItemAt = { it in transitionPositions },
        holderFor = { targetHolder },
        nowMs = { nowMs },
    )

    var transitionPositions: Set<Int> = emptySet()

    init {
        every { sourceHolder.isAtMinimumZoom() } returns true
        every { targetHolder.isAtMinimumZoom() } returns true
        every { targetHolder.isLaidOut } returns true
        every { pager.currentItem } answers { currentItemIndex }
        every { pager.acquireGestures(any()) } answers { gestureGate.acquire(firstArg()) }
        every { pager.releaseGestures(any()) } answers { gestureGate.release(firstArg()) }
        every { pager.post(any()) } answers {
            postedCallbacks.addLast(firstArg<Runnable>())
            true
        }
        every { pager.postDelayed(any(), any()) } answers {
            delayedCallbacks += firstArg<Runnable>()
            true
        }

        every { pager.removeCallbacks(any()) } answers {
            val runnable = firstArg<Runnable>()
            postedCallbacks.remove(runnable) || delayedCallbacks.remove(runnable)
        }
        every {
            overlay.playCurl(
                from = any(),
                to = any(),
                direction = any(),
                durationMs = any(),
                onEnd = any(),
            )
        } answers {
            playedDirections += arg<CurlDirection>(2)
            val token = playGate.begin()
            val onEnd = arg<() -> Unit>(4)
            endCallbacks += { if (playGate.isCurrent(token)) onEnd() }
        }
        every { overlay.abortAndHide() } answers { playGate.invalidate() }
    }

    fun startCurl() {
        coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            direction = CurlDirection.FROM_RIGHT,
            advance = {},
        )
        runNextPostedCallback()
    }

    /**
     * Runs a tap through the coordinator and drains all callbacks that the run
     * posted, so each tap starts from a settled state.
     */
    fun simulateTap(targetPosition: Int) {
        requestTap(targetPosition, CurlDirection.FROM_RIGHT)
    }

    fun requestTap(targetPosition: Int, direction: CurlDirection) {
        coordinator.runOrFallback(
            targetPosition = targetPosition,
            direction = direction,
            advance = { animate ->
                advancedAnimations += animate
                currentItemIndex = targetPosition
            },
        )
        while (postedCallbacks.isNotEmpty()) {
            postedCallbacks.removeFirst().run()
        }
    }

    fun completeCurl(playIndex: Int) {
        endCallbacks[playIndex].invoke()
        runNextPostedCallback()
        if (pendingPostedCount > 0) runNextPostedCallback()
    }

    fun runNewestPostedCallback() {
        postedCallbacks.removeLast().run()
    }

    fun runNextPostedCallback() {
        postedCallbacks.removeFirst().run()
    }

    fun nextPostedCallback(): Runnable {
        return postedCallbacks.first()
    }

    fun bitmap(): Bitmap {
        var recycled = false
        return mockk {
            every { isRecycled } answers { recycled }
            every { recycle() } answers { recycled = true }
        }
    }

    /** A captured-page handle wrapping one tracked mock bitmap. */
    fun page(): CapturedPage = CapturedPage(bitmap())

    private companion object {
        const val TARGET_POSITION = 1
        const val DEFAULT_PAGE_COUNT = TARGET_POSITION + 4
    }
}
