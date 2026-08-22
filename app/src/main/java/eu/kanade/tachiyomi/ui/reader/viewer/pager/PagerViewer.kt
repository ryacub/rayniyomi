package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.PointF
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.viewpager.widget.ViewPager
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.model.ChapterTransition
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.viewer.Viewer
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation.NavigationRegion
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.injectLazy
import kotlin.math.min

/**
 * Implementation of a [Viewer] to display pages with a [ViewPager].
 */
@Suppress("LeakingThis")
abstract class PagerViewer(val activity: ReaderActivity) : Viewer {

    val downloadManager: MangaDownloadManager by injectLazy()

    private val scope = MainScope()

    /**
     * View pager used by this viewer. It's abstract to implement L2R, R2L and vertical pagers on
     * top of this class.
     */
    val pager = createPager()

    /**
     * Configuration used by the pager, like allow taps, scale mode on images, page transitions...
     */
    val config = PagerConfig(this, scope)

    /**
     * Whether page changes play an animated transition.
     */
    private val useAnimatedTransition
        get() = config.pageTransitionStyle != ReaderPreferences.PageTransitionStyle.NONE

    /**
     * Adapter of the pager.
     */
    private val adapter = PagerViewerAdapter(this)

    /**
     * Currently active item. It can be a chapter page or a chapter transition.
     */
    private var currentPage: Any? = null

    /**
     * Container that holds the pager and the curl overlay. The activity receives this
     * container from [getView], so the overlay sits above the pager without touching
     * the pager's layout.
     */
    private val container = FrameLayout(activity)

    /**
     * Curl overlay for horizontal pagers. Vertical paging has no page-turn direction,
     * so vertical pagers leave this null and always fall back to slide or snap.
     */
    private val curlOverlay: PageCurlOverlayView? =
        if (this is L2RPagerViewer || this is R2LPagerViewer) PageCurlOverlayView(activity) else null

    /**
     * Monotonic id of the running curl. Stale end callbacks compare their captured id
     * against this field and ignore themselves when the ids differ.
     */
    private var curlGenerationId = 0L

    /**
     * Uptime timestamp of the previous page navigation. Turns inside the rapid
     * navigation window skip the curl and avoid queued animations.
     */
    private var lastNavigationAtMs = 0L

    /** Pending target-holder poll. Cleared in [destroy]. */
    private var curlTargetReadyRunnable: Runnable? = null

    /** Pending layout poll. Cleared in [destroy]. */
    private var curlLayoutCheckRunnable: Runnable? = null

    /**
     * Viewer chapters to set when the pager enters idle mode. Otherwise, if the view was settling
     * or dragging, there'd be a noticeable and annoying jump.
     */
    private var awaitingIdleViewerChapters: ViewerChapters? = null

    /**
     * Whether the view pager is currently in idle mode. It sets the awaiting chapters if setting
     * this field to true.
     */
    private var isIdle = true
        set(value) {
            field = value
            if (value) {
                awaitingIdleViewerChapters?.let { viewerChapters ->
                    setChaptersInternal(viewerChapters)
                    awaitingIdleViewerChapters = null
                    if (viewerChapters.currChapter.pages?.size == 1) {
                        adapter.nextTransition?.to?.let(activity::requestPreloadChapter)
                    }
                }
            }
        }

    init {
        pager.isVisible = false // Don't layout the pager yet
        pager.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        pager.isFocusable = false
        pager.offscreenPageLimit = 1
        pager.id = R.id.reader_pager
        container.layoutParams = FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
        )
        container.addView(
            pager,
            FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
        curlOverlay?.let { overlay ->
            container.addView(
                overlay,
                FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
            )
        }
        pager.adapter = adapter
        pager.addOnPageChangeListener(
            object : ViewPager.SimpleOnPageChangeListener() {
                override fun onPageSelected(position: Int) {
                    if (!activity.isScrollingThroughPages) {
                        activity.hideMenu()
                    }
                    onPageChange(position)
                }

                override fun onPageScrollStateChanged(state: Int) {
                    isIdle = state == ViewPager.SCROLL_STATE_IDLE
                }
            },
        )
        pager.tapListener = { event ->
            val viewPosition = IntArray(2)
            pager.getLocationOnScreen(viewPosition)
            val viewPositionRelativeToWindow = IntArray(2)
            pager.getLocationInWindow(viewPositionRelativeToWindow)
            val pos = PointF(
                (event.rawX - viewPosition[0] + viewPositionRelativeToWindow[0]) / pager.width,
                (event.rawY - viewPosition[1] + viewPositionRelativeToWindow[1]) / pager.height,
            )
            when (config.navigator.getAction(pos)) {
                NavigationRegion.MENU -> activity.toggleMenu()
                NavigationRegion.NEXT -> moveToNext()
                NavigationRegion.PREV -> moveToPrevious()
                NavigationRegion.RIGHT -> moveRight()
                NavigationRegion.LEFT -> moveLeft()
            }
        }
        pager.longTapListener = f@{
            if (activity.viewModel.state.value.menuVisible || config.longTapEnabled) {
                val item = adapter.items.getOrNull(pager.currentItem)
                if (item is ReaderPage) {
                    activity.onPageLongTap(item)
                    return@f true
                }
            }
            false
        }

        config.dualPageSplitChangedListener = { enabled ->
            if (!enabled) {
                cleanupPageSplit()
            }
        }

        config.imagePropertyChangedListener = {
            refreshAdapter()
        }

        config.navigationModeChangedListener = {
            val showOnStart = config.navigationOverlayOnStart || config.forceNavigationOverlay
            activity.setNavigationOverlay(config.navigator, showOnStart)
        }
    }

    override fun destroy() {
        super.destroy()
        curlTargetReadyRunnable?.let(pager::removeCallbacks)
        curlTargetReadyRunnable = null
        curlLayoutCheckRunnable?.let(pager::removeCallbacks)
        curlLayoutCheckRunnable = null
        curlOverlay?.cancelCurl()
        scope.cancel()
    }

    /**
     * Creates a new ViewPager.
     */
    abstract fun createPager(): Pager

    /**
     * Returns the view this viewer uses.
     */
    override fun getView(): View {
        return container
    }

    /**
     * Returns the PagerPageHolder for the provided page
     */
    private fun getPageHolder(page: ReaderPage): PagerPageHolder? =
        pager.children
            .filterIsInstance(PagerPageHolder::class.java)
            .firstOrNull { it.item == page }

    /**
     * Called when a new page (either a [ReaderPage] or [ChapterTransition]) is marked as active
     */
    private fun onPageChange(position: Int) {
        val page = adapter.items.getOrNull(position)
        if (page != null && currentPage != page) {
            val allowPreload = checkAllowPreload(page as? ReaderPage)
            val forward = when {
                currentPage is ReaderPage && page is ReaderPage -> {
                    // if both pages have the same number, it's a split page with an InsertPage
                    if (page.number == (currentPage as ReaderPage).number) {
                        // the InsertPage is always the second in the reading direction
                        page is InsertPage
                    } else {
                        page.number > (currentPage as ReaderPage).number
                    }
                }
                currentPage is ChapterTransition.Prev && page is ReaderPage ->
                    false
                else -> true
            }
            currentPage = page
            when (page) {
                is ReaderPage -> onReaderPageSelected(page, allowPreload, forward)
                is ChapterTransition -> onTransitionSelected(page)
            }
        }
    }

    private fun checkAllowPreload(page: ReaderPage?): Boolean {
        // Page is transition page - preload allowed
        page ?: return true

        // Initial opening - preload allowed
        currentPage ?: return true

        // Allow preload for
        // 1. Going to next chapter from chapter transition
        // 2. Going between pages of same chapter
        // 3. Next chapter page
        return when (page.chapter) {
            (currentPage as? ChapterTransition.Next)?.to -> true
            (currentPage as? ReaderPage)?.chapter -> true
            adapter.nextTransition?.to -> true
            else -> false
        }
    }

    /**
     * Called when a [ReaderPage] is marked as active. It notifies the
     * activity of the change and requests the preload of the next chapter if this is the last page.
     */
    private fun onReaderPageSelected(page: ReaderPage, allowPreload: Boolean, forward: Boolean) {
        val pages = page.chapter.pages ?: return
        logcat { "onReaderPageSelected: ${page.number}/${pages.size}" }
        activity.onPageSelected(page)

        // Notify holder of page change
        getPageHolder(page)?.onPageSelected(forward)

        // Skip preload on inserts it causes unwanted page jumping
        if (page is InsertPage) {
            return
        }

        // Preload next chapter once we're within the last 5 pages of the current chapter
        val inPreloadRange = pages.size - page.number < 5
        if (inPreloadRange && allowPreload && page.chapter == adapter.currentChapter) {
            logcat { "Request preload next chapter because we're at page ${page.number} of ${pages.size}" }
            adapter.nextTransition?.to?.let(activity::requestPreloadChapter)
        }
    }

    /**
     * Called when a [ChapterTransition] is marked as active. It request the
     * preload of the destination chapter of the transition.
     */
    private fun onTransitionSelected(transition: ChapterTransition) {
        logcat { "onTransitionSelected: $transition" }
        val toChapter = transition.to
        if (toChapter != null) {
            logcat { "Request preload destination chapter because we're on the transition" }
            activity.requestPreloadChapter(toChapter)
        } else if (transition is ChapterTransition.Next) {
            // No more chapters, show menu because the user is probably going to close the reader
            activity.showMenu()
        }
    }

    /**
     * Tells this viewer to set the given [chapters] as active. If the pager is currently idle,
     * it sets the chapters immediately, otherwise they are saved and set when it becomes idle.
     */
    override fun setChapters(chapters: ViewerChapters) {
        if (isIdle) {
            setChaptersInternal(chapters)
        } else {
            awaitingIdleViewerChapters = chapters
        }
    }

    /**
     * Sets the active [chapters] on this pager.
     */
    private fun setChaptersInternal(chapters: ViewerChapters) {
        val forceTransition = config.alwaysShowChapterTransition ||
            adapter.items.getOrNull(
                pager.currentItem,
            ) is ChapterTransition
        adapter.setChapters(chapters, forceTransition)

        // Layout the pager once a chapter is being set
        if (pager.isGone) {
            logcat { "Pager first layout" }
            val pages = chapters.currChapter.pages ?: return
            moveToPage(pages[min(chapters.currChapter.requestedPage, pages.lastIndex)])
            pager.isVisible = true
        }
    }

    /**
     * Tells this viewer to move to the given [page].
     */
    override fun moveToPage(page: ReaderPage) {
        val position = adapter.items.indexOf(page)
        if (position != -1) {
            val currentPosition = pager.currentItem
            pager.setCurrentItem(position, true)
            // manually call onPageChange since ViewPager listener is not triggered in this case
            if (currentPosition == position) {
                onPageChange(position)
            }
        } else {
            logcat { "Page $page not found in adapter" }
        }
    }

    /**
     * Moves to the next page.
     */
    open fun moveToNext() {
        moveRight()
    }

    /**
     * Moves to the previous page.
     */
    open fun moveToPrevious() {
        moveLeft()
    }

    /**
     * Moves to the page at the right.
     */
    protected open fun moveRight() {
        if (pager.currentItem != adapter.count - 1) {
            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
            if (holder != null && config.navigateToPan && holder.canPanRight()) {
                holder.panRight()
            } else {
                attemptCurlThenAdvance(targetPosition = pager.currentItem + 1, curlFromRight = true)
            }
        }
    }

    /**
     * Moves to the page at the left.
     */
    protected open fun moveLeft() {
        if (pager.currentItem != 0) {
            val holder = (currentPage as? ReaderPage)?.let(::getPageHolder)
            if (holder != null && config.navigateToPan && holder.canPanLeft()) {
                holder.panLeft()
            } else {
                attemptCurlThenAdvance(targetPosition = pager.currentItem - 1, curlFromRight = false)
            }
        }
    }

    /**
     * Attempts to play the curl animation before advancing to [targetPosition]. Falls
     * back to slide or snap when any curl precondition fails or the capture fails.
     */
    private fun attemptCurlThenAdvance(targetPosition: Int, curlFromRight: Boolean) {
        val nowMs = SystemClock.uptimeMillis()
        val preference = config.pageTransitionStyle
        val withinRapidNavigationWindow = nowMs - lastNavigationAtMs < RAPID_NAVIGATION_WINDOW_MS
        lastNavigationAtMs = nowMs

        // effectiveTransitionStyle reads the system animator scale through a
        // ContentResolver. Consult it only for CURL because other styles ignore it.
        val style = if (preference == ReaderPreferences.PageTransitionStyle.CURL) {
            config.effectiveTransitionStyle(activity)
        } else {
            preference
        }
        val useAnimation = style != ReaderPreferences.PageTransitionStyle.NONE
        val overlay = curlOverlay
        val sourceHolder = (currentPage as? ReaderPage)?.let(::getPageHolder)
        val targetItem = adapter.items.getOrNull(targetPosition)

        // ViewPager keeps the adjacent holder alive (offscreenPageLimit = 1), so an
        // existing holder reports its real zoom state. A holder ViewPager has not
        // created yet cannot be mid-zoom, so absence means at-minimum zoom.
        val targetHolder = (targetItem as? ReaderPage)?.let(::getPageHolder)

        val canAttemptCurl = overlay != null &&
            sourceHolder != null &&
            shouldAttemptCurl(
                style = style,
                targetIsChapterTransition = targetItem is ChapterTransition,
                sourceAtMinimumZoom = sourceHolder.isAtMinimumZoom(),
                targetAtMinimumZoom = targetHolder?.isAtMinimumZoom() ?: true,
                withinRapidNavigationWindow = withinRapidNavigationWindow,
            )

        if (!canAttemptCurl) {
            pager.setCurrentItem(targetPosition, useAnimation)
            return
        }

        val fromBitmap = overlay.captureBitmap(sourceHolder)
        if (fromBitmap == null) {
            pager.setCurrentItem(targetPosition, useAnimation)
            return
        }

        // Snap now so ViewPager creates the target view under the overlay.
        pager.setCurrentItem(targetPosition, false)
        pager.setGestureDetectorEnabled(false)

        var waitAttempts = 0
        val checkTargetReady = Runnable {
            val item = adapter.items.getOrNull(targetPosition)
            // Reuse the holder found before the snap. The poll only covers the rare
            // case where ViewPager had not created the holder yet.
            val readyHolder = (item as? ReaderPage)?.let(::getPageHolder) ?: targetHolder

            if (readyHolder != null || waitAttempts >= TARGET_WAIT_MAX_ATTEMPTS) {
                curlTargetReadyRunnable = null
                playAndHideCurl(fromBitmap, readyHolder, overlay, curlFromRight)
            } else {
                waitAttempts++
                val pending = curlTargetReadyRunnable
                if (pending != null) {
                    pager.postDelayed(pending, TARGET_POLL_INTERVAL_MS)
                }
            }
        }
        curlTargetReadyRunnable = checkTargetReady
        pager.post(checkTargetReady)
    }

    /**
     * Plays the curl animation from [fromBitmap] to a capture of [targetHolder]. The
     * generation id lets stale end callbacks detect that another curl replaced them.
     */
    private fun playAndHideCurl(
        fromBitmap: Bitmap,
        targetHolder: PagerPageHolder?,
        overlay: PageCurlOverlayView,
        curlFromRight: Boolean,
    ) {
        val toBitmap = targetHolder?.let { overlay.captureBitmap(it) }
        if (toBitmap == null) {
            fromBitmap.recycle()
            pager.setGestureDetectorEnabled(true)
            return
        }

        val generationId = ++curlGenerationId
        overlay.playCurl(
            from = fromBitmap,
            to = toBitmap,
            curlFromRight = curlFromRight,
            durationMs = CURL_DURATION_MS,
            onEnd = {
                if (generationId == curlGenerationId) {
                    waitForLayoutThenHideCurl(overlay, fromBitmap, toBitmap)
                } else {
                    // A newer curl owns the overlay and the gesture state, so this
                    // stale callback recycles only its own bitmaps.
                    fromBitmap.recycle()
                    toBitmap.recycle()
                }
            },
        )
    }

    /**
     * Waits until the current page holder is laid out, then hides the overlay and
     * recycles the bitmaps. The attempt cap bounds the wait as a fail-safe.
     */
    private fun waitForLayoutThenHideCurl(
        overlay: PageCurlOverlayView,
        fromBitmap: Bitmap,
        toBitmap: Bitmap,
    ) {
        var layoutCheckAttempts = 0
        val checkLayout = Runnable {
            val item = adapter.items.getOrNull(pager.currentItem)
            val isLaidOut = (item as? ReaderPage)?.let(::getPageHolder)?.isLaidOut == true

            if (isLaidOut || layoutCheckAttempts >= LAYOUT_WAIT_MAX_ATTEMPTS) {
                curlLayoutCheckRunnable = null
                // Clear the bitmap references before recycling so a later draw pass
                // cannot touch recycled bitmaps.
                overlay.isVisible = false
                overlay.cancelCurl()
                fromBitmap.recycle()
                toBitmap.recycle()

                pager.postDelayed({ pager.setGestureDetectorEnabled(true) }, GESTURE_REENABLE_DELAY_MS)
            } else {
                layoutCheckAttempts++
                val pending = curlLayoutCheckRunnable
                if (pending != null) {
                    pager.postDelayed(pending, TARGET_POLL_INTERVAL_MS)
                }
            }
        }
        curlLayoutCheckRunnable = checkLayout
        pager.post(checkLayout)
    }

    /**
     * Moves to the page at the top (or previous).
     */
    protected open fun moveUp() {
        moveToPrevious()
    }

    /**
     * Moves to the page at the bottom (or next).
     */
    protected open fun moveDown() {
        moveToNext()
    }

    /**
     * Resets the adapter in order to recreate all the views. Used when a image configuration is
     * changed.
     */
    private fun refreshAdapter() {
        val currentItem = pager.currentItem
        adapter.refresh()
        pager.adapter = adapter
        pager.setCurrentItem(currentItem, false)
    }

    /**
     * Called from the containing activity when a key [event] is received. It should return true
     * if the event was handled, false otherwise.
     */
    override fun handleKeyEvent(event: KeyEvent): Boolean {
        val isUp = event.action == KeyEvent.ACTION_UP
        val ctrlPressed = event.metaState.and(KeyEvent.META_CTRL_ON) > 0

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveDown() else moveUp()
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (!config.volumeKeysEnabled || activity.viewModel.state.value.menuVisible) {
                    return false
                } else if (isUp) {
                    if (!config.volumeKeysInverted) moveUp() else moveDown()
                }
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isUp) {
                    if (ctrlPressed) moveToNext() else moveRight()
                }
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isUp) {
                    if (ctrlPressed) moveToPrevious() else moveLeft()
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_DPAD_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_PAGE_DOWN -> if (isUp) moveDown()
            KeyEvent.KEYCODE_PAGE_UP -> if (isUp) moveUp()
            KeyEvent.KEYCODE_MENU -> if (isUp) activity.toggleMenu()
            else -> return false
        }
        return true
    }

    /**
     * Called from the containing activity when a generic motion [event] is received. It should
     * return true if the event was handled, false otherwise.
     */
    override fun handleGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_CLASS_POINTER != 0) {
            when (event.action) {
                MotionEvent.ACTION_SCROLL -> {
                    if (event.getAxisValue(MotionEvent.AXIS_VSCROLL) < 0.0f) {
                        moveDown()
                    } else {
                        moveUp()
                    }
                    return true
                }
            }
        }
        return false
    }

    fun onPageSplit(currentPage: ReaderPage, newPage: InsertPage) {
        activity.runOnUiThread {
            // Need to insert on UI thread else images will go blank
            adapter.onPageSplit(currentPage, newPage)
        }
    }

    private fun cleanupPageSplit() {
        adapter.cleanupPageSplit()
    }

    companion object {
        private const val CURL_DURATION_MS = 300L
        private const val RAPID_NAVIGATION_WINDOW_MS = 500L
        private const val TARGET_POLL_INTERVAL_MS = 10L
        private const val TARGET_WAIT_MAX_ATTEMPTS = 10
        private const val LAYOUT_WAIT_MAX_ATTEMPTS = 50
        private const val GESTURE_REENABLE_DELAY_MS = 50L

        /**
         * Returns whether a page turn should attempt the curl animation.
         * The function has no side effects, so tests run it without a view hierarchy.
         */
        fun shouldAttemptCurl(
            style: ReaderPreferences.PageTransitionStyle,
            targetIsChapterTransition: Boolean,
            sourceAtMinimumZoom: Boolean,
            targetAtMinimumZoom: Boolean,
            withinRapidNavigationWindow: Boolean,
        ): Boolean {
            if (style != ReaderPreferences.PageTransitionStyle.CURL) return false
            if (targetIsChapterTransition) return false
            if (!sourceAtMinimumZoom || !targetAtMinimumZoom) return false
            if (withinRapidNavigationWindow) return false
            return true
        }
    }
}
