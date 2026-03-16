package eu.kanade.tachiyomi.ui.download.anime

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeDownloadQueueScreenModel(
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
) : ScreenModel {

    private val _state = MutableStateFlow(emptyList<AnimeDownloadUiHeaderItem>())
    val state = _state.asStateFlow()

    private val collapsedSources = MutableStateFlow(emptySet<Long>())

    init {
        screenModelScope.launch {
            downloadManager.queueState
                .map { downloads ->
                    val collapsedIds = collapsedSources.value
                    downloads
                        .groupBy { it.source }
                        .map { (source, sourceDownloads) ->
                            AnimeDownloadUiHeaderItem(
                                source = source,
                                downloads = sourceDownloads.map { download ->
                                    AnimeDownloadUiItem(
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

    fun toggleExpanded(source: AnimeHttpSource) {
        collapsedSources.update { current ->
            if (current.contains(source.id)) {
                current - source.id
            } else {
                current + source.id
            }
        }
    }

    fun collapseAll() {
        val currentSources = _state.value
        collapsedSources.update { currentSources.map { it.source.id }.toSet() }
    }

    fun expandHeader(source: AnimeHttpSource) {
        collapsedSources.update { it - source.id }
    }

    fun moveToTop(item: AnimeDownloadUiItem) {
        val currentState = _state.value
        val newDownloads = mutableListOf<AnimeDownload>()
        currentState.forEach { header ->
            val mutable = header.downloads.toMutableList()
            val idx = mutable.indexOfFirst { it.download == item.download }
            if (idx > 0) {
                mutable.removeAt(idx)
                mutable.add(0, item)
            }
            newDownloads.addAll(mutable.map { it.download })
        }
        reorder(newDownloads)
    }

    fun moveToBottom(item: AnimeDownloadUiItem) {
        val currentState = _state.value
        val newDownloads = mutableListOf<AnimeDownload>()
        currentState.forEach { header ->
            val mutable = header.downloads.toMutableList()
            val idx = mutable.indexOfFirst { it.download == item.download }
            if (idx >= 0 && idx < mutable.size - 1) {
                mutable.removeAt(idx)
                mutable.add(mutable.size, item)
            }
            newDownloads.addAll(mutable.map { it.download })
        }
        reorder(newDownloads)
    }

    fun moveToTopSeries(animeId: Long) {
        val all = _state.value.flatMap { it.downloads }
        val (series, others) = all.partition { it.download.anime.id == animeId }
        reorder((series + others).map { it.download })
    }

    fun moveToBottomSeries(animeId: Long) {
        val all = _state.value.flatMap { it.downloads }
        val (series, others) = all.partition { it.download.anime.id == animeId }
        reorder((others + series).map { it.download })
    }

    fun cancelDownload(item: AnimeDownloadUiItem) {
        cancel(listOf(item.download))
    }

    fun cancelSeries(animeId: Long) {
        val series = _state.value.flatMap { it.downloads }
            .filter { it.download.anime.id == animeId }
            .map { it.download }
        if (series.isNotEmpty()) {
            cancel(series)
        }
    }

    override fun onDispose() {}

    val isDownloaderRunning = downloadManager.isDownloaderRunning
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    fun reorder(downloads: List<AnimeDownload>) {
        downloadManager.reorderQueue(downloads)
    }

    fun cancel(downloads: List<AnimeDownload>) {
        downloadManager.cancelQueuedDownloads(downloads)
    }

    fun <R : Comparable<R>> reorderQueue(
        selector: (AnimeDownloadUiItem) -> R,
        reverse: Boolean = false,
    ) {
        val currentState = _state.value
        val newAnimeDownloads = mutableListOf<AnimeDownload>()
        currentState.forEach { headerItem ->
            val sortedItems = headerItem.downloads.sortedBy(selector).let {
                if (reverse) it.reversed() else it
            }
            newAnimeDownloads.addAll(sortedItems.map { it.download })
        }
        reorder(newAnimeDownloads)
    }
}
