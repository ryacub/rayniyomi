package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

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
    restorePending: Boolean = false,
) {
    private val lock = Any()
    private val deferredMutations = ArrayDeque<() -> Unit>()
    private val ready = CompletableDeferred<Unit>().also { if (!restorePending) it.complete(Unit) }

    suspend fun awaitRestore() = ready.await()

    suspend fun restore(load: suspend () -> List<D>) {
        try {
            val restored = load()
            synchronized(lock) {
                restored.forEach(markQueued)
                _queueState.value = restored
                while (deferredMutations.isNotEmpty()) deferredMutations.removeFirst()()
                ready.complete(Unit)
            }
        } catch (e: Exception) {
            synchronized(lock) {
                deferredMutations.clear()
                ready.completeExceptionally(e)
            }
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Could not restore download queue" }
        }
    }

    fun addAll(downloads: List<D>) = submit {
        val current = _queueState.value
        val added = newDownloads(current, downloads)
        if (added.isEmpty()) return@submit
        store.addAll(added)
        added.forEach(markQueued)
        _queueState.value = current + added
    }

    fun remove(download: D) = submit {
        store.remove(download)
        markInactive(download)
        _queueState.value = _queueState.value - download
    }

    fun removeIf(onRemoved: (List<D>) -> Unit = {}, predicate: (D) -> Boolean) = submit {
        val current = _queueState.value
        val removed = current.filter(predicate)
        store.removeAll(removed)
        removed.forEach(markInactive)
        _queueState.value = current - removed.toSet()
        onRemoved(removed)
    }

    fun internalClear(onRemoved: (List<D>) -> Unit = {}) = submit {
        val current = _queueState.value
        store.clear()
        current.forEach(markInactive)
        _queueState.value = emptyList()
        onRemoved(current)
    }

    fun replaceAll(downloads: List<D>) = submit {
        store.replaceAll(downloads)
        _queueState.value.forEach(markInactive)
        downloads.forEach(markQueued)
        _queueState.value = downloads
    }

    fun addToStart(downloads: List<D>) = submit {
        val current = _queueState.value
        val added = newDownloads(current, downloads)
        downloads.forEach(markQueued)
        if (added.isEmpty()) return@submit
        val ordered = added + current
        store.replaceAll(ordered)
        _queueState.value = ordered
    }

    fun moveToFront(download: D) = submit {
        val ordered = listOf(download) + _queueState.value.filterNot { itemId(it) == itemId(download) }
        store.replaceAll(ordered)
        markQueued(download)
        _queueState.value = ordered
    }

    private fun newDownloads(current: List<D>, downloads: List<D>): List<D> {
        val existingIds = current.mapTo(mutableSetOf(), itemId)
        return downloads.filter { existingIds.add(itemId(it)) }
    }

    private fun submit(mutation: () -> Unit) {
        synchronized(lock) {
            when {
                ready.isCancelled -> return
                ready.isCompleted -> mutation()
                else -> deferredMutations.addLast(mutation)
            }
        }
    }
}
