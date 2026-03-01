package eu.kanade.tachiyomi.data.download.model

/**
 * Shared status transitions and queue summary helpers across download domains.
 */
object DownloadStatusTracker {
    /**
     * Treat a running transfer as stalled after 10s with no byte progress.
     * This is long enough to avoid jitter false positives while still surfacing
     * "not starting" feedback quickly to users.
     */
    const val STALL_THRESHOLD_MS = 10_000L

    /**
     * Aggregated queue status used by notifications.
     */
    data class QueueStatusSummary(
        val downloading: Int = 0,
        val waitingForSlot: Int = 0,
        val stalled: Int = 0,
    )

    /**
     * Returns true when a running transfer has not progressed within the threshold.
     */
    fun shouldMarkStalled(download: DownloadStatusSnapshot, nowMs: Long): Boolean {
        if (!download.isRunningTransfer) return false
        if (download.displayStatus == DownloadDisplayStatus.RETRYING) return false
        val lastProgressAt = download.lastProgressAt
        if (lastProgressAt <= 0L) return false
        return nowMs - lastProgressAt >= STALL_THRESHOLD_MS
    }

    /**
     * Counts key queue states for compact user-visible summaries.
     */
    fun summarize(downloads: List<DownloadStatusSnapshot>): QueueStatusSummary {
        var downloading = 0
        var waitingForSlot = 0
        var stalled = 0
        downloads.forEach { download ->
            when (download.displayStatus) {
                DownloadDisplayStatus.DOWNLOADING -> downloading++
                DownloadDisplayStatus.WAITING_FOR_SLOT -> waitingForSlot++
                DownloadDisplayStatus.STALLED -> stalled++
                else -> Unit
            }
        }
        return QueueStatusSummary(
            downloading = downloading,
            waitingForSlot = waitingForSlot,
            stalled = stalled,
        )
    }
}
