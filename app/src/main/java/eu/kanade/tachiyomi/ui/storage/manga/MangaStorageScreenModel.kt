package eu.kanade.tachiyomi.ui.storage.manga

import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.ui.storage.CommonStorageScreenModel
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.manga.interactor.GetVisibleMangaCategories
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.source.manga.service.MangaSourceManager

class MangaStorageScreenModel(
    downloadCache: MangaDownloadCache = appGraph.mangaDownloadCache,
    private val getLibraries: GetLibraryManga = appGraph.getLibraryManga,
    getCategories: GetMangaCategories = appGraph.getMangaCategories,
    getVisibleCategories: GetVisibleMangaCategories = appGraph.getVisibleMangaCategories,
    private val downloadManager: MangaDownloadManager = appGraph.mangaDownloadManager,
    private val sourceManager: MangaSourceManager = appGraph.mangaSourceManager,
) : CommonStorageScreenModel<LibraryManga>(
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
    getDownloadSize = { downloadManager.getDownloadSize(manga) },
    getDownloadCount = { downloadManager.getDownloadCount(manga) },
    getId = { id },
    getCategoryId = { category },
    getTitle = { manga.title },
    getThumbnail = { manga.thumbnailUrl },
) {
    override fun deleteEntry(id: Long) {
        screenModelScope.launchNonCancellable {
            val manga = getLibraries.await().find {
                it.id == id
            }?.manga ?: return@launchNonCancellable
            val source = sourceManager.get(manga.source) ?: return@launchNonCancellable
            downloadManager.deleteManga(manga, source)
        }
    }
}
