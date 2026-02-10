package eu.kanade.tachiyomi.data.download.core

/**
 * Interface for queue persistence operations shared by anime and manga download stores.
 */
interface DownloadQueueStore<D> {
    fun addAll(downloads: List<D>)
    fun remove(download: D)
    fun removeAll(downloads: List<D>)
    fun clear()
}
