package eu.kanade.tachiyomi.ui.storage.anime

import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.ui.storage.CommonStorageScreenModel
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.GetVisibleAnimeCategories
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager

class AnimeStorageScreenModel(
    downloadCache: AnimeDownloadCache = appGraph.animeDownloadCache,
    private val getLibraries: GetLibraryAnime = appGraph.getLibraryAnime,
    getCategories: GetAnimeCategories = appGraph.getAnimeCategories,
    getVisibleCategories: GetVisibleAnimeCategories = appGraph.getVisibleAnimeCategories,
    private val downloadManager: AnimeDownloadManager = appGraph.animeDownloadManager,
    private val sourceManager: AnimeSourceManager = appGraph.animeSourceManager,
) : CommonStorageScreenModel<LibraryAnime>(
    downloadCacheChanges = downloadCache.changes,
    downloadCacheIsInitializing = downloadCache.isInitializing,
    libraries = getLibraries.subscribe(),
    categories = { hideHiddenCategories ->
        if (hideHiddenCategories) {
            getVisibleCategories.subscribe()
        } else {
            getCategories.subscribe()
        }
    },
    getDownloadSize = { downloadManager.getDownloadSize(anime) },
    getDownloadCount = { downloadManager.getDownloadCount(anime) },
    getId = { id },
    getCategoryId = { category },
    getTitle = { anime.title },
    getThumbnail = { anime.thumbnailUrl },
) {
    override fun deleteEntry(id: Long) {
        viewModelScope.launchNonCancellable {
            val anime = getLibraries.await().find {
                it.id == id
            }?.anime ?: return@launchNonCancellable
            val source = sourceManager.get(anime.source) ?: return@launchNonCancellable
            downloadManager.deleteAnime(anime, source)
        }
    }
}
