package eu.kanade.tachiyomi.ui.download

interface DownloadQueueEntry {
    val downloadId: Long?
    val entryId: Long
    val key: String
    val name: String
    val progress: Float
}

interface DownloadQueueGroup<I : DownloadQueueEntry> {
    val sourceId: Long
    val sourceName: String
    val downloads: List<I>
    val isExpanded: Boolean
}

interface DownloadQueueActions<I : DownloadQueueEntry> {
    fun reorder(downloadIds: List<Long>)
    fun collapseAll()
    fun expandHeader(sourceId: Long)
    fun toggleExpanded(sourceId: Long)
    fun moveToTop(item: I)
    fun moveToBottom(item: I)
    fun moveToTopSeries(entryId: Long)
    fun moveToBottomSeries(entryId: Long)
    fun cancelDownload(item: I)
    fun cancelSeries(entryId: Long)
}
