package eu.kanade.tachiyomi.data.download.core

/**
 * Side effects emitted by [DownloadQueueReducer].
 */
sealed interface DownloadQueueEffect {
    data class MoveToFrontById(val downloadId: Long) : DownloadQueueEffect
    data class ReorderByIds(val orderedIds: List<Long>) : DownloadQueueEffect
    data class AddToStartByIds(val downloadIds: List<Long>) : DownloadQueueEffect
    data class RemoveByItemIds(val itemIds: List<Long>) : DownloadQueueEffect

    data object StartDownloads : DownloadQueueEffect
    data object PauseDownloader : DownloadQueueEffect
    data object StartDownloader : DownloadQueueEffect
    data object StopDownloader : DownloadQueueEffect
}
