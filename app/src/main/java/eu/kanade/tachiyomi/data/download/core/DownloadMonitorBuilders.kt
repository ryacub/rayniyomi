package eu.kanade.tachiyomi.data.download.core

import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.data.download.model.DownloadStatusSnapshot
import eu.kanade.tachiyomi.data.download.model.DownloadStatusTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Builds the endless progress and stall monitors that run beside a download.
 */
object DownloadMonitorBuilders {

    /**
     * Returns a monitor that reports byte progress for [download].
     *
     * The monitor collects [progressFlow]. When the download is running, it
     * records the progress time, resets the retry count, sets the status, and
     * calls [onProgress].
     */
    fun <T : DownloadStatusSnapshot> progressMonitor(
        download: T,
        progressFlow: Flow<*>,
        onProgress: (T) -> Unit,
    ): suspend () -> Unit = {
        progressFlow.collect {
            if (download.isRunningTransfer) {
                download.lastProgressAt = System.currentTimeMillis()
                download.retryAttempt = 0
                download.displayStatus = DownloadDisplayStatus.DOWNLOADING
                onProgress(download)
            }
        }
    }

    /**
     * Returns a monitor that marks [download] as stalled when it stops progressing.
     *
     * The monitor loops while the download runs. It waits one second between
     * checks and calls [onProgress] when the download becomes stalled.
     */
    fun <T : DownloadStatusSnapshot> stallMonitor(
        download: T,
        onProgress: (T) -> Unit,
    ): suspend () -> Unit = {
        while (download.isRunningTransfer) {
            delay(1_000)
            val now = System.currentTimeMillis()
            if (DownloadStatusTracker.shouldMarkStalled(download, now)) {
                download.displayStatus = DownloadDisplayStatus.STALLED
                onProgress(download)
            }
        }
    }

    /**
     * Returns the progress and stall monitors in start order.
     *
     * DownloadMonitors.withMonitors cancels in reverse list order, so stall
     * reporting stops before progress collection, to avoid a stale state update.
     */
    fun <T : DownloadStatusSnapshot> monitors(
        download: T,
        progressFlow: Flow<*>,
        onProgress: (T) -> Unit,
    ): List<suspend () -> Unit> = listOf(
        progressMonitor(download, progressFlow, onProgress),
        stallMonitor(download, onProgress),
    )
}
