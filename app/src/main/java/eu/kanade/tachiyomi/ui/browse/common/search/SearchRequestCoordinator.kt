package eu.kanade.tachiyomi.ui.browse.common.search

import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates concurrent search requests and allows callers to ignore stale results.
 *
 * Thread-safe: uses lock-free [AtomicLong] for request ID coordination.
 *
 * Overflow behavior: After 2^63-1 requests, counter wraps to negative values.
 * This is practically impossible (300+ years at 1000 requests/second).
 */
internal class SearchRequestCoordinator {

    private val latestRequestId = AtomicLong(0L)

    /**
     * Generates a new request ID for tracking search requests.
     *
     * Thread-safe: can be called concurrently from multiple coroutines.
     *
     * @return A unique request ID that increments with each call
     */
    fun nextRequestId(): Long = latestRequestId.incrementAndGet()

    /**
     * Checks if the given request ID is the most recent request.
     *
     * Used to gate state updates so that stale async results don't overwrite
     * newer search results.
     *
     * Thread-safe: can be called concurrently from multiple coroutines.
     *
     * @param requestId The request ID to check
     * @return true if this is the latest request, false if superseded by a newer request
     */
    fun isLatest(requestId: Long): Boolean = latestRequestId.get() == requestId
}
