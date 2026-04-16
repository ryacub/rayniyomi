package eu.kanade.tachiyomi.data.download.core

/**
 * Serialized queue mutation commands.
 *
 * Command payloads are ID-based so stale object snapshots from callers
 * cannot directly mutate queue state.
 */
sealed interface DownloadQueueCommand {
    data class StartNow(val downloadId: Long) : DownloadQueueCommand
    data class Reorder(val orderedIds: List<Long>) : DownloadQueueCommand
    data class AddToStart(val downloadIds: List<Long>) : DownloadQueueCommand
    data class RemoveByItemIds(val itemIds: List<Long>) : DownloadQueueCommand
}
