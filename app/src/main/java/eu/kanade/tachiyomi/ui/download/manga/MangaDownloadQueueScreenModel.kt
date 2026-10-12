package eu.kanade.tachiyomi.ui.download.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.ui.download.DownloadQueueActions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
class MangaDownloadQueueScreenModel(
    private val downloadManager: MangaDownloadManager,
) : ViewModel(), DownloadQueueActions<MangaDownloadUiItem> {

    private val _state = MutableStateFlow(emptyList<MangaDownloadUiHeaderItem>())
    val state = _state.asStateFlow()

    private val collapsedSources = MutableStateFlow(emptySet<Long>())

    init {
        viewModelScope.launch {
            downloadManager.queueState
                .combine(collapsedSources) { downloads, collapsedIds ->
                    downloads
                        .groupBy { it.source }
                        .map { (source, sourceDownloads) ->
                            MangaDownloadUiHeaderItem(
                                source = source,
                                downloads = sourceDownloads.map { download ->
                                    MangaDownloadUiItem(
                                        download = download,
                                        progress = download.progress / 100f,
                                    )
                                },
                                isExpanded = !collapsedIds.contains(source.id),
                            )
                        }
                }
                .distinctUntilChanged()
                .collect { newList -> _state.update { newList } }
        }
    }

    override fun toggleExpanded(sourceId: Long) {
        collapsedSources.update { current ->
            if (current.contains(sourceId)) {
                current - sourceId
            } else {
                current + sourceId
            }
        }
    }

    override fun collapseAll() {
        val currentSources = _state.value
        collapsedSources.update { currentSources.map { it.source.id }.toSet() }
    }

    override fun expandHeader(sourceId: Long) {
        collapsedSources.update { it - sourceId }
    }

    override fun moveToTop(item: MangaDownloadUiItem) {
        val currentState = _state.value
        val reorderedChapterIds = mutableListOf<Long>()
        currentState.forEach { header ->
            val mutable = header.downloads.toMutableList()
            val idx = mutable.indexOfFirst { it.download == item.download }
            if (idx > 0) {
                mutable.removeAt(idx)
                mutable.add(0, item)
            }
            reorderedChapterIds.addAll(mutable.mapNotNull { it.download.chapter.id })
        }
        reorder(reorderedChapterIds)
    }

    override fun moveToBottom(item: MangaDownloadUiItem) {
        val currentState = _state.value
        val reorderedChapterIds = mutableListOf<Long>()
        currentState.forEach { header ->
            val mutable = header.downloads.toMutableList()
            val idx = mutable.indexOfFirst { it.download == item.download }
            if (idx >= 0 && idx < mutable.size - 1) {
                mutable.removeAt(idx)
                mutable.add(mutable.size, item)
            }
            reorderedChapterIds.addAll(mutable.mapNotNull { it.download.chapter.id })
        }
        reorder(reorderedChapterIds)
    }

    override fun moveToTopSeries(entryId: Long) {
        val all = _state.value.flatMap { it.downloads }
        val (series, others) = all.partition { it.download.manga.id == entryId }
        reorder((series + others).mapNotNull { it.download.chapter.id })
    }

    override fun moveToBottomSeries(entryId: Long) {
        val all = _state.value.flatMap { it.downloads }
        val (series, others) = all.partition { it.download.manga.id == entryId }
        reorder((others + series).mapNotNull { it.download.chapter.id })
    }

    override fun cancelDownload(item: MangaDownloadUiItem) {
        cancel(listOf(item.download))
    }

    override fun cancelSeries(entryId: Long) {
        val series = _state.value.flatMap { it.downloads }
            .filter { it.download.manga.id == entryId }
            .map { it.download }
        if (series.isNotEmpty()) {
            cancel(series)
        }
    }

    val isDownloaderRunning = downloadManager.isDownloaderRunning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun getDownloadStatusFlow() = downloadManager.statusFlow()
    fun getDownloadProgressFlow() = downloadManager.progressFlow()

    fun startDownloads() {
        downloadManager.startDownloads()
    }

    fun pauseDownloads() {
        downloadManager.pauseDownloads()
    }

    fun clearQueue() {
        downloadManager.clearQueue()
    }

    override fun reorder(downloadIds: List<Long>) {
        viewModelScope.launch {
            downloadManager.reorderQueueByChapterIds(downloadIds)
        }
    }

    fun cancel(downloads: List<MangaDownload>) {
        downloadManager.cancelQueuedDownloads(downloads)
    }

    fun <R : Comparable<R>> reorderQueue(
        selector: (MangaDownloadUiItem) -> R,
        reverse: Boolean = false,
    ) {
        val currentState = _state.value
        val newDownloadIds = mutableListOf<Long>()
        currentState.forEach { headerItem ->
            val sortedItems = headerItem.downloads.sortedBy(selector).let {
                if (reverse) it.reversed() else it
            }
            newDownloadIds.addAll(sortedItems.mapNotNull { it.download.chapter.id })
        }
        reorder(newDownloadIds)
    }
}
