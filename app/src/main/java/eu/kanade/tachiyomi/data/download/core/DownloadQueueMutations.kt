package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Shared queue mutation orchestration for download managers.
 *
 * Keeps queue mutations consistent across anime and manga managers while
 * preserving their downloader-specific behavior via callbacks.
 *
 * Thread-safe: All public methods use internal [queueMutex] for synchronization.
 * All callbacks are invoked on the caller's coroutine context.
 *
 * @param D Download type (e.g., AnimeDownload, MangaDownload) - the item being downloaded
 * @param I Entity type that owns downloads (e.g., Episode, Chapter) - used for removal operations
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

    /**
     * Finds a queued download by its ID.
     *
     * @param id The download ID to search for
     * @return The download if found in queue, null otherwise
     */
    fun getQueuedDownloadOrNull(id: Long): D? {
        return queueState.value.find { itemId(it) == id }
    }

    /**
     * Moves a download to the front of the queue and starts downloads.
     *
     * If the download is not in the queue, attempts to create it from the ID.
     * If creation fails (returns null), silently returns.
     *
     * @param id The download ID to prioritize
     */
    suspend fun startDownloadNow(id: Long) {
        val existingDownload = getQueuedDownloadOrNull(id)
        val toAdd = existingDownload ?: run {
            try {
                createFromId(id)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to create download from ID $id: ${e.message}" }
                null
            }
        } ?: return

        try {
            moveToFront(toAdd)
            startDownloads()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to start download now for ID $id: ${e.message}" }
        }
    }

    /**
     * Reorders the download queue to match the provided list.
     *
     * @param downloads The new queue order
     */
    fun reorderQueue(downloads: List<D>) {
        try {
            updateQueue(downloads)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to reorder queue: ${e.message}" }
            throw e
        }
    }

    /**
     * Adds downloads to the start of the queue and optionally starts the downloader.
     *
     * @param downloads The downloads to add
     * @param startIfNeeded Callback to start the downloader if needed (e.g., check if job is running)
     */
    fun addDownloadsToStart(downloads: List<D>, startIfNeeded: () -> Unit) {
        if (downloads.isEmpty()) return
        try {
            addToStart(downloads)
            startIfNeeded()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to add downloads to start: ${e.message}" }
            throw e
        }
    }

    /**
     * Removes items from the queue safely with proper downloader state management.
     *
     * Thread-safe: Acquires [queueMutex] to ensure atomic pause-remove-restart sequence.
     * If downloader was running:
     * - Pauses downloader before removal
     * - If queue becomes empty after removal, stops downloader
     * - Otherwise, restarts downloader
     *
     * @param items The entities (episodes/chapters) to remove from the queue
     */
    suspend fun removeFromQueueSafely(items: List<I>) {
        queueMutex.withLock {
            val wasRunning = try {
                isRunning()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to check if downloader is running: ${e.message}" }
                false
            }

            if (wasRunning) {
                try {
                    pauseDownloader()
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Failed to pause downloader: ${e.message}" }
                    // Continue with removal even if pause fails
                }
            }

            try {
                removeFromQueue(items)
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to remove items from queue: ${e.message}" }
                // Attempt to restart if we paused, even if removal failed
                if (wasRunning) {
                    try {
                        startDownloader()
                    } catch (restartError: Exception) {
                        logcat(LogPriority.ERROR) {
                            "Failed to restart downloader after removal error: ${restartError.message}"
                        }
                    }
                }
                throw e
            }

            if (wasRunning) {
                try {
                    if (queueState.value.isEmpty()) {
                        stopDownloader()
                    } else {
                        startDownloader()
                    }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR) { "Failed to restart downloader: ${e.message}" }
                    throw e
                }
            }
        }
    }
}
