package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Generic queue mutation operations shared by anime and manga downloaders.
 *
 * Uses composition with function parameters to avoid forcing download models
 * to implement a common interface (which would blast into 18+ files).
 *
 * @param D the download type (AnimeDownload or MangaDownload)
 * @param _queueState the mutable state flow backing the download queue
 * @param store the persistence store for queue operations
 * @param itemId extracts the item ID (episode or chapter) from a download
 * @param isActive returns true if the download is in DOWNLOADING or QUEUE state
 * @param markQueued sets the download status to QUEUE
 * @param markInactive sets the download status to NOT_DOWNLOADED if currently active
 */
class DownloadQueueOperations<D : Any>(
    private val _queueState: MutableStateFlow<List<D>>,
    private val store: DownloadQueueStore<D>,
    private val itemId: (D) -> Long,
    private val isActive: (D) -> Boolean,
    private val markQueued: (D) -> Unit,
    private val markInactive: (D) -> Unit,
) {

    fun addAll(downloads: List<D>) {
        _queueState.update {
            downloads.forEach { download -> markQueued(download) }
            store.addAll(downloads)
            it + downloads
        }
    }

    fun remove(download: D) {
        _queueState.update {
            store.remove(download)
            markInactive(download)
            it - download
        }
    }

    fun removeIf(predicate: (D) -> Boolean) {
        _queueState.update { queue ->
            val downloads = queue.filter { predicate(it) }
            store.removeAll(downloads)
            downloads.forEach { markInactive(it) }
            queue - downloads.toSet()
        }
    }

    fun internalClear() {
        _queueState.update {
            it.forEach { download -> markInactive(download) }
            store.clear()
            emptyList()
        }
    }

    fun addToStart(downloads: List<D>) {
        _queueState.update { currentQueue ->
            downloads.forEach { download ->
                markQueued(download)
                store.addAll(listOf(download))
            }
            val existingIds = currentQueue.map { itemId(it) }.toSet()
            val newDownloads = downloads.filterNot { itemId(it) in existingIds }
            newDownloads + currentQueue
        }
    }

    fun moveToFront(download: D) {
        _queueState.update { currentQueue ->
            val filtered = currentQueue.filterNot { itemId(it) == itemId(download) }
            markQueued(download)
            store.addAll(listOf(download))
            listOf(download) + filtered
        }
    }
}
