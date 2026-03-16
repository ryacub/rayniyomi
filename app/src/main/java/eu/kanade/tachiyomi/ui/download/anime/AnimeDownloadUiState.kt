package eu.kanade.tachiyomi.ui.download.anime

import androidx.compose.runtime.Stable
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload

@Stable
data class AnimeDownloadUiHeaderItem(
    val source: AnimeHttpSource,
    val downloads: List<AnimeDownloadUiItem>,
    val isExpanded: Boolean = true,
)

@Stable
data class AnimeDownloadUiItem(
    val download: AnimeDownload,
    val progress: Float,
)
