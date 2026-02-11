package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared queue mutation orchestration for download managers.
 *
 * Keeps queue mutations consistent across anime and manga managers while
 * preserving their downloader-specific behavior via callbacks.
 */
class DownloadQueueMutations<D : Any, I : Any>(
    private val queueState: StateFlow<List<D>>,
    private val itemId: (D) -> Long,
    private val createFromId: suspend (Long) -> D?,
    private val moveToFront: (D) -> Unit,
    private val updateQueue: (List<D>) -> Unit,
    private val addToStart: (List<D>) -> Unit,
    private val removeFromQueue: (List<I>) -> Unit,
    private val isRunning: () -> Boolean,
    private val pauseDownloader: () -> Unit,
    private val startDownloader: () -> Unit,
    private val stopDownloader: () -> Unit,
    private val startDownloads: () -> Unit,
    private val queueMutex: Mutex,
) {

    fun getQueuedDownloadOrNull(id: Long): D? {
        return queueState.value.find { itemId(it) == id }
    }

    suspend fun startDownloadNow(id: Long) {
        val existingDownload = getQueuedDownloadOrNull(id)
        val toAdd = existingDownload ?: createFromId(id) ?: return
        moveToFront(toAdd)
        startDownloads()
    }

    fun reorderQueue(downloads: List<D>) {
        updateQueue(downloads)
    }

    fun addDownloadsToStart(downloads: List<D>, startIfNeeded: () -> Unit) {
        if (downloads.isEmpty()) return
        addToStart(downloads)
        startIfNeeded()
    }

    suspend fun removeFromQueueSafely(items: List<I>) {
        queueMutex.withLock {
            val wasRunning = isRunning()
            if (wasRunning) {
                pauseDownloader()
            }

            removeFromQueue(items)

            if (wasRunning) {
                if (queueState.value.isEmpty()) {
                    stopDownloader()
                } else {
                    startDownloader()
                }
            }
        }
    }
}
