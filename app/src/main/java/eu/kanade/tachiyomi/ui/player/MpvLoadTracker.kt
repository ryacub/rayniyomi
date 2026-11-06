package eu.kanade.tachiyomi.ui.player

/** Reserves one mpv load at a time until mpv reports its start. */
internal class MpvLoadTracker<T : Any> {
    internal class Reservation<T : Any> internal constructor(val item: T)

    private var latest: Reservation<T>? = null
    private var reserved: Reservation<T>? = null
    private var queued: Reservation<T>? = null
    private var issued: Reservation<T>? = null
    private var started: Reservation<T>? = null
    private var loaded: Reservation<T>? = null
    private var closed = false

    /** Returns a reservation for immediate UI dispatch, or queues the latest request. */
    @Synchronized
    fun requestLoad(item: T): Reservation<T>? {
        if (closed) return null

        val request = Reservation(item)
        latest = request
        loaded = null
        if (reserved == null && issued == null) {
            reserved = request
            return request
        }

        queued = request
        return null
    }

    /** Starts a previously reserved request. The caller must run native setup on the UI thread. */
    @Synchronized
    fun onLoadIssued(reservation: Reservation<T>): Boolean {
        if (closed || reserved !== reservation) return false

        reserved = null
        issued = reservation
        return true
    }

    /** Acknowledges the issued load and reserves the latest queued request before UI dispatch. */
    @Synchronized
    fun onFileStarted(): Reservation<T>? {
        val startedRequest = issued
        if (startedRequest != null) {
            started = startedRequest
            issued = null
        }

        val next = queued ?: return null
        if (closed || reserved != null || issued != null) return null

        queued = null
        reserved = next
        return next
    }

    /** Associates FILE_LOADED with a started request without clearing a newer pending start. */
    @Synchronized
    fun onFileLoaded() {
        val completed = started ?: return
        started = null
        if (latest === completed) loaded = completed
    }

    @Synchronized
    fun onLoadError(): T? {
        started = null
        if (closed || issued != null || reserved != null || loaded === latest) return null
        return latest?.item.also { latest = null }
    }

    @Synchronized
    fun reservedLoad(): Reservation<T>? = reserved

    @Synchronized
    fun loadedItem(): T? = loaded?.takeIf { it === latest }?.item

    @Synchronized
    fun close() {
        closed = true
        latest = null
        reserved = null
        queued = null
        issued = null
        started = null
        loaded = null
    }
}
