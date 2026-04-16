package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Shared queue mutation orchestration for download managers.
 *
 * Keeps queue mutations consistent across anime and manga managers while
 * preserving their downloader-specific behavior via callbacks.
 *
 * Thread-safe: all mutations are serialized through an internal actor.
 *
 * @param D Download type (e.g., AnimeDownload, MangaDownload) - the item being downloaded
 * @param I Entity type that owns downloads (e.g., Episode, Chapter) - used for removal operations
 */
class DownloadQueueMutations<D : Any, I : Any>(
    private val queueState: StateFlow<List<D>>,
    private val itemId: (D) -> Long,
    private val ownerItemId: (I) -> Long,
    private val createFromId: suspend (Long) -> D?,
    private val moveToFront: (D) -> Unit,
    private val updateQueue: (List<D>) -> Unit,
    private val addToStart: (List<D>) -> Unit,
    private val removeFromQueueByIds: (List<Long>) -> Unit,
    private val isRunning: () -> Boolean,
    private val pauseDownloader: () -> Unit,
    private val startDownloader: () -> Unit,
    private val stopDownloader: () -> Unit,
    private val startDownloads: () -> Unit,
    mutationScope: CoroutineScope,
) {
    private val queueActor = DownloadQueueActor(mutationScope)

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
        queueActor.submit(DownloadQueueCommand.StartNow(id)) {
            runReducerCommand(DownloadQueueCommand.StartNow(id))
        }
    }

    /**
     * Reorders the download queue to match the provided list.
     *
     * @param downloads The new queue order
     */
    @Deprecated(
        message = "Use reorderQueueByIds to avoid stale object snapshot payloads",
        replaceWith = ReplaceWith("reorderQueueByIds(downloads.map(itemId))"),
    )
    suspend fun reorderQueue(downloads: List<D>) {
        val ids = downloads.map(itemId)
        reorderQueueByIds(ids)
    }

    suspend fun reorderQueueByIds(downloadIds: List<Long>) {
        queueActor.submit(DownloadQueueCommand.Reorder(downloadIds)) {
            runReducerCommand(DownloadQueueCommand.Reorder(downloadIds))
        }
    }

    /**
     * Adds downloads to the start of the queue and optionally starts the downloader.
     *
     * @param downloads The downloads to add
     * @param startIfNeeded Callback to start the downloader if needed (e.g., check if job is running)
     */
    @Deprecated(
        message = "Use addDownloadsToStartByIds to avoid stale object snapshot payloads",
        replaceWith = ReplaceWith("addDownloadsToStartByIds(downloads.map(itemId), startIfNeeded)"),
    )
    suspend fun addDownloadsToStart(downloads: List<D>, startIfNeeded: () -> Unit) {
        val ids = downloads.map(itemId)
        addDownloadsToStartByIds(ids, startIfNeeded)
    }

    suspend fun addDownloadsToStartByIds(downloadIds: List<Long>, startIfNeeded: () -> Unit) {
        if (downloadIds.isEmpty()) return
        queueActor.submit(DownloadQueueCommand.AddToStart(downloadIds)) {
            runReducerCommand(DownloadQueueCommand.AddToStart(downloadIds), startIfNeeded)
        }
    }

    /**
     * Removes items from the queue safely with proper downloader state management.
     *
     * Thread-safe: all commands are serialized by [DownloadQueueActor].
     *
     * @param items The entities (episodes/chapters) to remove from the queue
     */
    suspend fun removeFromQueueSafely(items: List<I>) {
        removeFromQueueSafelyByItemIds(items.map(ownerItemId))
    }

    suspend fun removeFromQueueSafelyByItemIds(itemIds: List<Long>) {
        if (itemIds.isEmpty()) return
        queueActor.submit(DownloadQueueCommand.RemoveByItemIds(itemIds)) {
            runReducerCommand(DownloadQueueCommand.RemoveByItemIds(itemIds))
        }
    }

    private suspend fun runReducerCommand(
        command: DownloadQueueCommand,
        startIfNeeded: (() -> Unit)? = null,
    ) {
        val state = DownloadQueueReducerState(
            downloadIds = queueState.value.map(itemId),
            isDownloaderRunning = try {
                isRunning()
            } catch (_: Exception) {
                false
            },
        )
        val result = DownloadQueueReducer.reduce(state, command)

        var skipStartDownloads = false
        for (effect in result.effects) {
            if (skipStartDownloads && effect == DownloadQueueEffect.StartDownloads) {
                continue
            }
            val applied = applyEffect(effect, startIfNeeded)
            if (!applied && effect is DownloadQueueEffect.MoveToFrontById) {
                skipStartDownloads = true
            }
        }
    }

    private suspend fun applyEffect(
        effect: DownloadQueueEffect,
        startIfNeeded: (() -> Unit)?,
    ): Boolean {
        try {
            when (effect) {
                is DownloadQueueEffect.MoveToFrontById -> {
                    val existing = queueState.value.find { itemId(it) == effect.downloadId }
                    val toMove = existing ?: createFromId(effect.downloadId) ?: return false
                    moveToFront(toMove)
                }
                is DownloadQueueEffect.ReorderByIds -> {
                    val currentById = queueState.value.associateBy(itemId)
                    val ordered = effect.orderedIds.mapNotNull { currentById[it] }
                    updateQueue(ordered)
                }
                is DownloadQueueEffect.AddToStartByIds -> {
                    val currentById = queueState.value.associateBy(itemId)
                    val toAdd = effect.downloadIds.mapNotNull { id ->
                        currentById[id] ?: createFromId(id)
                    }
                    if (toAdd.isNotEmpty()) {
                        addToStart(toAdd)
                    }
                    startIfNeeded?.invoke() ?: startDownloads()
                }
                is DownloadQueueEffect.RemoveByItemIds -> {
                    removeFromQueueByIds(effect.itemIds)
                }
                DownloadQueueEffect.PauseDownloader -> pauseDownloader()
                DownloadQueueEffect.StartDownloader -> startDownloader()
                DownloadQueueEffect.StopDownloader -> stopDownloader()
                DownloadQueueEffect.StartDownloads -> startDownloads()
            }
            return true
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Queue effect failed: $effect (${e.message})" }
            throw e
        }
    }
}
