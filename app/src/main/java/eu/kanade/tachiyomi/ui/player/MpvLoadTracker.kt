package eu.kanade.tachiyomi.ui.player

/**
 * Tells which `loadfile` an mpv end-file error belongs to.
 *
 * mpv's end-file error carries no file identity, so the tracker counts loads it was asked for
 * against the start-file events mpv reports. An error is attributed to the latest load only when
 * that load has started and has not loaded yet. An error from an older file, or from a file that
 * already played, returns null. Calls arrive from the caller's thread and from mpv's event thread.
 */
internal class MpvLoadTracker<T : Any> {
    private var latest: T? = null
    private var pendingStarts = 0
    private var loaded = false

    @Synchronized
    fun onLoadIssued(item: T) {
        latest = item
        pendingStarts++
        loaded = false
    }

    @Synchronized
    fun onFileStarted() {
        if (pendingStarts > 0) pendingStarts--
        loaded = false
    }

    /** A file that loads proves no older start is still pending, which also repairs a start mpv skipped. */
    @Synchronized
    fun onFileLoaded() {
        pendingStarts = 0
        loaded = true
    }

    /** Returns the item whose load failed, or null if the error is stale or happened after loading. */
    @Synchronized
    fun onLoadError(): T? {
        if (pendingStarts > 0 || loaded) return null
        return latest.also { latest = null }
    }
}
