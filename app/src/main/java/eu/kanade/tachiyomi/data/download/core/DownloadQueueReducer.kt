package eu.kanade.tachiyomi.data.download.core

data class DownloadQueueReducerState(
    val downloadIds: List<Long>,
    val isDownloaderRunning: Boolean,
)

data class DownloadQueueReducerResult(
    val nextState: DownloadQueueReducerState,
    val effects: List<DownloadQueueEffect>,
)

/**
 * Pure reducer for queue mutation commands.
 *
 * Invariants:
 * - Queue IDs remain unique in reducer state.
 * - Reorder never introduces unknown IDs.
 * - Remove cannot resurrect IDs.
 */
object DownloadQueueReducer {

    fun reduce(
        state: DownloadQueueReducerState,
        command: DownloadQueueCommand,
    ): DownloadQueueReducerResult {
        return when (command) {
            is DownloadQueueCommand.StartNow -> reduceStartNow(state, command)
            is DownloadQueueCommand.Reorder -> reduceReorder(state, command)
            is DownloadQueueCommand.AddToStart -> reduceAddToStart(state, command)
            is DownloadQueueCommand.RemoveByItemIds -> reduceRemoveByItemIds(state, command)
        }
    }

    private fun reduceStartNow(
        state: DownloadQueueReducerState,
        command: DownloadQueueCommand.StartNow,
    ): DownloadQueueReducerResult {
        val updated = listOf(command.downloadId) + state.downloadIds.filterNot { it == command.downloadId }
        return DownloadQueueReducerResult(
            nextState = state.copy(downloadIds = updated),
            effects = listOf(
                DownloadQueueEffect.MoveToFrontById(command.downloadId),
                DownloadQueueEffect.StartDownloads,
            ),
        )
    }

    private fun reduceReorder(
        state: DownloadQueueReducerState,
        command: DownloadQueueCommand.Reorder,
    ): DownloadQueueReducerResult {
        val current = state.downloadIds
        val requested = command.orderedIds.distinct()
        val normalized = requested.filter { it in current }
        val normalizedSet = normalized.toSet()
        val merged = normalized + current.filterNot { it in normalizedSet }

        val effects = if (merged == current) {
            emptyList()
        } else {
            listOf(DownloadQueueEffect.ReorderByIds(merged))
        }

        return DownloadQueueReducerResult(
            nextState = state.copy(downloadIds = merged),
            effects = effects,
        )
    }

    private fun reduceAddToStart(
        state: DownloadQueueReducerState,
        command: DownloadQueueCommand.AddToStart,
    ): DownloadQueueReducerResult {
        val currentSet = state.downloadIds.toSet()
        val newIds = command.downloadIds.distinct().filterNot { it in currentSet }
        if (newIds.isEmpty()) {
            return DownloadQueueReducerResult(nextState = state, effects = emptyList())
        }

        return DownloadQueueReducerResult(
            nextState = state.copy(downloadIds = newIds + state.downloadIds),
            effects = listOf(
                DownloadQueueEffect.AddToStartByIds(newIds),
                DownloadQueueEffect.StartDownloads,
            ),
        )
    }

    private fun reduceRemoveByItemIds(
        state: DownloadQueueReducerState,
        command: DownloadQueueCommand.RemoveByItemIds,
    ): DownloadQueueReducerResult {
        val removeIds = command.itemIds.distinct()
        if (removeIds.isEmpty()) {
            return DownloadQueueReducerResult(nextState = state, effects = emptyList())
        }

        val removeSet = removeIds.toSet()
        val updated = state.downloadIds.filterNot { it in removeSet }

        val effects = buildList {
            if (state.isDownloaderRunning) {
                add(DownloadQueueEffect.PauseDownloader)
            }
            add(DownloadQueueEffect.RemoveByItemIds(removeIds))
            if (state.isDownloaderRunning) {
                if (updated.isEmpty()) {
                    add(DownloadQueueEffect.StopDownloader)
                } else {
                    add(DownloadQueueEffect.StartDownloader)
                }
            }
        }

        return DownloadQueueReducerResult(
            nextState = state.copy(downloadIds = updated),
            effects = effects,
        )
    }
}
