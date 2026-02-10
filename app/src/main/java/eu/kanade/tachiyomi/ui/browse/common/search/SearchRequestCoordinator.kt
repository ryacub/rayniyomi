package eu.kanade.tachiyomi.ui.browse.common.search

import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates concurrent search requests and allows callers to ignore stale results.
 */
internal class SearchRequestCoordinator {

    private val latestRequestId = AtomicLong(0L)

    fun nextRequestId(): Long = latestRequestId.incrementAndGet()

    fun isLatest(requestId: Long): Boolean = latestRequestId.get() == requestId
}
