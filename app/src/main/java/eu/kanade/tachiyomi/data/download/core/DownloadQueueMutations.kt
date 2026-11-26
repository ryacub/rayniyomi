package eu.kanade.tachiyomi.data.download.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
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
 */
class DownloadQueueMutations<D : Any>(
    private val queueState: StateFlow<List<D>>,
    private val itemId: (D) -> Long,
    private val createFromId: suspend (Long) -> D?,
    private val moveToFront: suspend (Long, D?) -> Boolean,
    private val updateQueue: suspend (List<Long>) -> Unit,
    private val addToStart: suspend (List<D>) -> Unit,
    private val removeFromQueueByIds: (List<Long>) -> Unit,
    private val isRunning: () -> Boolean,
    private val pauseDownloader: () -> Unit,
    private val startDownloader: () -> Unit,
    private val stopDownloader: () -> Unit,
    private val startDownloads: () -> Unit,
    private val mutationScope: CoroutineScope,
    private val retryDownload: (D) -> Unit = {},
    private val awaitQueueRestore: suspend () -> Unit = {},
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
        val command = DownloadQueueCommand.StartNow(id)
        mutationScope.async {
            queueActor.submit(command) { runReducerCommand(command) }
        }.await()
    }

    suspend fun retry(downloadId: Long) {
        val command = DownloadQueueCommand.Retry(downloadId)
        queueActor.submit(command) { runReducerCommand(command) }
    }

    suspend fun reorderQueueByIds(downloadIds: List<Long>) {
        queueActor.submit(DownloadQueueCommand.Reorder(downloadIds)) {
            runReducerCommand(DownloadQueueCommand.Reorder(downloadIds))
        }
    }

    suspend fun addDownloadsToStartByIds(downloadIds: List<Long>, startIfNeeded: () -> Unit) {
        if (downloadIds.isEmpty()) return
        queueActor.submit(DownloadQueueCommand.AddToStart(downloadIds)) {
            runReducerCommand(DownloadQueueCommand.AddToStart(downloadIds), startIfNeeded)
        }
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
        awaitQueueRestore()
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
                is DownloadQueueEffect.RetryById -> {
                    getQueuedDownloadOrNull(effect.downloadId)?.let(retryDownload)
                }
                is DownloadQueueEffect.MoveToFrontById -> {
                    val alreadyQueued = getQueuedDownloadOrNull(effect.downloadId) != null
                    val newDownload = if (alreadyQueued) null else createFromId(effect.downloadId)
                    return moveToFront(effect.downloadId, newDownload)
                }
                is DownloadQueueEffect.ReorderByIds -> updateQueue(effect.orderedIds)
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
