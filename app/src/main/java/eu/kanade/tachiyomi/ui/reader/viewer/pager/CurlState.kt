package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.viewer.pager.PageCurlCapture.CapturedPage

/** The callback kinds one curl tracks. Each kind holds one runnable. */
internal enum class TrackedRole {

    /** Polls until the target holder exists. */
    TARGET_POLL,

    /** Polls until the target holder finishes layout. */
    LAYOUT_POLL,

    /** Reenables gestures a short delay after teardown. */
    GESTURE_REENABLE,
}

/**
 * One curl's tracked runnables and captured pages. Main-thread only:
 * ViewPager callbacks, posted runnables, and the view animator all run
 * there, so the fields need no synchronization.
 */
internal class CurlState {
    var targetPosition: Int? = null

    /** One tracked callback per role. Main-thread only. */
    private val slots = arrayOfNulls<Runnable>(TrackedRole.entries.size)

    var pendingFromPage: CapturedPage? = null
    var activeFromPage: CapturedPage? = null
    var activeToPage: CapturedPage? = null

    /**
     * Stores [runnable] as the current callback for [role] and returns
     * it, so call sites can post the same instance in one expression.
     */
    fun track(role: TrackedRole, runnable: Runnable): Runnable {
        slots[role.ordinal] = runnable
        return runnable
    }

    /**
     * Releases [role]'s slot once its runnable has fired. A fired
     * callback no longer counts as a tracked resource.
     */
    fun fire(role: TrackedRole) {
        slots[role.ordinal] = null
    }

    /** True while [runnable] is still the tracked callback for [role]. */
    fun isCurrent(role: TrackedRole, runnable: Runnable): Boolean =
        slots[role.ordinal] === runnable

    /**
     * True when no tracked resource remains. The teardown guard uses only
     * this predicate: no invariant ties resources to other state, so the
     * guard never skips teardown and never leaks pages or the gesture
     * claim.
     */
    fun isEmpty(): Boolean =
        slots.all { it == null } &&
            pendingFromPage == null &&
            activeFromPage == null &&
            activeToPage == null

    fun clearRunnables(scheduler: PageCurlScheduler) {
        for (slot in slots) slot?.let(scheduler::removeCallbacks)
        slots.fill(null)
    }

    /**
     * Tears down tracked callbacks and position, closes every tracked
     * page, and aborts the overlay. Runs [abortOverlay] after closing,
     * so a reentrant animation-end callback observes recycled bitmaps.
     *
     * Callers must check [isEmpty] first; this member assumes work exists.
     */
    fun finish(
        scheduler: PageCurlScheduler,
        abortOverlay: () -> Unit,
    ) {
        clearRunnables(scheduler)
        targetPosition = null
        pendingFromPage?.close()
        pendingFromPage = null
        activeFromPage?.close()
        activeFromPage = null
        activeToPage?.close()
        activeToPage = null
        abortOverlay()
    }
}
