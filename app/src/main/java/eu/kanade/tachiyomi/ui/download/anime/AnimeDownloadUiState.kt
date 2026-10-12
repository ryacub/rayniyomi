package eu.kanade.tachiyomi.ui.download.anime

import androidx.compose.runtime.Stable
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.ui.download.DownloadQueueEntry
import eu.kanade.tachiyomi.ui.download.DownloadQueueGroup

@Stable
data class AnimeDownloadUiHeaderItem(
    val source: AnimeHttpSource,
    override val downloads: List<AnimeDownloadUiItem>,
    override val isExpanded: Boolean = true,
) : DownloadQueueGroup<AnimeDownloadUiItem> {
    override val sourceId get() = source.id
    override val sourceName get() = source.name
}

@Stable
data class AnimeDownloadUiItem(
    val download: AnimeDownload,
    override val progress: Float,
) : DownloadQueueEntry {
    override val downloadId get() = download.episode.id
    override val entryId get() = download.anime.id
    override val key get() = download.episode.url
    override val name get() = download.episode.name
}
