package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
    private val scope: CoroutineScope,
    restorePending: Boolean = false,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Any()
    private val pendingMutations = ArrayDeque<() -> Boolean>()
    private var isDraining = restorePending
    private val ready = CompletableDeferred<Unit>().also { if (!restorePending) it.complete(Unit) }

    suspend fun awaitRestore() = ready.await()

    suspend fun restore(load: suspend () -> List<D>) {
        val restored = try {
            load()
        } catch (e: Exception) {
            synchronized(lock) {
                pendingMutations.clear()
                ready.completeExceptionally(e)
            }
            if (e is CancellationException) throw e
            logcat(LogPriority.ERROR, e) { "Could not restore download queue" }
            return
        }
        restored.forEach(markQueued)
        _queueState.value = restored
        submit { ready.complete(Unit) }
        drain()
    }

    fun addAll(downloads: List<D>) = enqueue({ downloads }) {}

    fun enqueue(prepare: () -> List<D>, onAdded: (wasEmpty: Boolean) -> Unit) = submit {
        val current = _queueState.value
        val added = newDownloads(current, prepare())
        if (added.isEmpty()) return@submit
        store.addAll(added)
        added.forEach(markQueued)
        _queueState.value = current + added
        onAdded(current.isEmpty())
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

    suspend fun replaceAll(downloads: List<D>) = submitDurable {
        store.replaceAll(downloads)
        _queueState.value.forEach(markInactive)
        downloads.forEach(markQueued)
        _queueState.value = downloads
    }

    suspend fun addToStart(downloads: List<D>) = submitDurable {
        val current = _queueState.value
        val added = newDownloads(current, downloads)
        if (added.isEmpty()) {
            downloads.forEach(markQueued)
            return@submitDurable
        }
        val ordered = added + current
        store.replaceAll(ordered)
        downloads.forEach(markQueued)
        _queueState.value = ordered
    }

    suspend fun moveToFront(download: D) = submitDurable {
        val ordered = listOf(download) + _queueState.value.filterNot { itemId(it) == itemId(download) }
        store.replaceAll(ordered)
        markQueued(download)
        _queueState.value = ordered
    }

    private fun newDownloads(current: List<D>, downloads: List<D>): List<D> {
        val existingIds = current.mapTo(mutableSetOf(), itemId)
        return downloads.filter { existingIds.add(itemId(it)) }
    }

    private fun submit(mutation: () -> Unit) = schedule {
        mutation()
        true
    }

    private suspend fun submitDurable(mutation: suspend () -> Unit) {
        awaitRestore()
        val completed = CompletableDeferred<Unit>()
        schedule {
            scope.launch(ioDispatcher) {
                try {
                    mutation()
                } catch (e: Exception) {
                    completed.completeExceptionally(e)
                    if (e is CancellationException) throw e
                }
            }.invokeOnCompletion { failure ->
                if (failure == null) completed.complete(Unit) else completed.completeExceptionally(failure)
                drain()
            }
            false
        }
        completed.await()
    }

    // Lock only the FIFO. Queue and preference changes belong to its current owner.
    private fun schedule(mutation: () -> Boolean) {
        synchronized(lock) {
            if (ready.isCancelled) return
            pendingMutations.addLast(mutation)
            if (isDraining) return
            isDraining = true
        }
        drain()
    }

    private fun drain() {
        while (true) {
            val next = synchronized(lock) {
                pendingMutations.removeFirstOrNull() ?: run {
                    isDraining = false
                    return
                }
            }
            try {
                if (!next()) return // The durable mutation continues draining when it finishes.
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Could not update download queue" }
            }
        }
    }
}
