package eu.kanade.tachiyomi.data.download.model

object DownloadStatusTracker {
    const val STALL_THRESHOLD_MS = 10_000L

    data class QueueStatusSummary(
        val downloading: Int = 0,
        val waitingForSlot: Int = 0,
        val stalled: Int = 0,
    )

    fun shouldMarkStalled(download: DownloadStatusSnapshot, nowMs: Long): Boolean {
        if (!download.isRunningTransfer) return false
        if (download.displayStatus == DownloadDisplayStatus.RETRYING) return false
        val lastProgressAt = download.lastProgressAt
        if (lastProgressAt <= 0L) return false
        return nowMs - lastProgressAt >= STALL_THRESHOLD_MS
    }

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
