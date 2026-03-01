package eu.kanade.tachiyomi.data.download.anime.model

object AnimeDownloadStatusTracker {
    const val STALL_THRESHOLD_MS = 10_000L

    data class QueueStatusSummary(
        val downloading: Int = 0,
        val waitingForSlot: Int = 0,
        val stalled: Int = 0,
    )

    fun shouldMarkStalled(download: AnimeDownload, nowMs: Long): Boolean {
        if (download.status != AnimeDownload.State.DOWNLOADING) return false
        if (download.displayStatus == AnimeDownload.DisplayStatus.RETRYING) return false
        val lastProgressAt = download.lastProgressAt
        if (lastProgressAt <= 0L) return false
        return nowMs - lastProgressAt >= STALL_THRESHOLD_MS
    }

    fun summarize(downloads: List<AnimeDownload>): QueueStatusSummary {
        var downloading = 0
        var waitingForSlot = 0
        var stalled = 0
        downloads.forEach { download ->
            when (download.displayStatus) {
                AnimeDownload.DisplayStatus.DOWNLOADING -> downloading++
                AnimeDownload.DisplayStatus.WAITING_FOR_SLOT -> waitingForSlot++
                AnimeDownload.DisplayStatus.STALLED -> stalled++
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
