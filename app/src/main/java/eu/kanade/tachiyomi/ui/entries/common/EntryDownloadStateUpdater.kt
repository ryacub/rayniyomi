package eu.kanade.tachiyomi.ui.entries.common

internal object EntryDownloadStateUpdater {
    fun <T> update(
        items: List<T>,
        entryId: Long,
        idSelector: (T) -> Long,
        updateItem: (T) -> T,
    ): List<T> {
        val modifiedIndex = items.indexOfFirst { idSelector(it) == entryId }
        if (modifiedIndex < 0) return items

        return items.toMutableList().apply {
            this[modifiedIndex] = updateItem(this[modifiedIndex])
        }
    }
}
