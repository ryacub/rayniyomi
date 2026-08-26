package eu.kanade.tachiyomi.ui.reader.viewer.pager

/**
 * Schedules the coordinator's tracked callbacks. Production wires this to
 * the pager's handler facilities. Main-thread only, so implementations need
 * no synchronization.
 */
internal interface PageCurlScheduler {
    fun post(runnable: Runnable): Boolean

    fun postDelayed(runnable: Runnable, delayMs: Long): Boolean

    fun removeCallbacks(runnable: Runnable)
}
