package eu.kanade.tachiyomi.ui.download.manga

import androidx.compose.runtime.Stable
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.download.DownloadQueueEntry
import eu.kanade.tachiyomi.ui.download.DownloadQueueGroup

@Stable
data class MangaDownloadUiHeaderItem(
    val source: HttpSource,
    override val downloads: List<MangaDownloadUiItem>,
    override val isExpanded: Boolean = true,
) : DownloadQueueGroup<MangaDownloadUiItem> {
    override val sourceId get() = source.id
    override val sourceName get() = source.name
}

@Stable
data class MangaDownloadUiItem(
    val download: MangaDownload,
    override val progress: Float,
) : DownloadQueueEntry {
    override val downloadId get() = download.chapter.id
    override val entryId get() = download.manga.id
    override val key get() = download.chapter.url
    override val name get() = download.chapter.name
}
