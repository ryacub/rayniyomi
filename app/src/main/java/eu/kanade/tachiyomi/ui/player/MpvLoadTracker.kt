package eu.kanade.tachiyomi.ui.player

/** Matches an mpv end-file error, which names no file, to the latest load by counting start-file events. */
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

    // Also repairs the count if mpv skipped a start.
    @Synchronized
    fun onFileLoaded() {
        pendingStarts = 0
        loaded = true
    }

    @Synchronized
    fun onLoadError(): T? {
        if (pendingStarts > 0 || loaded) return null
        return latest.also { latest = null }
    }
}
