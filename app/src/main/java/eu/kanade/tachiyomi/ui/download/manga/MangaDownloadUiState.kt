package eu.kanade.tachiyomi.ui.download.manga

import androidx.compose.runtime.Stable
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload

@Stable
data class MangaDownloadUiHeaderItem(
    val source: HttpSource,
    val downloads: List<MangaDownloadUiItem>,
    val isExpanded: Boolean = true,
)

@Stable
data class MangaDownloadUiItem(
    val download: MangaDownload,
    val progress: Float,
)
