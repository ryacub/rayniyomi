package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import eu.kanade.domain.source.manga.model.RemoteMangaUpdate
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.ui.entries.common.InFlightRefreshes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import tachiyomi.domain.entries.manga.model.Manga

class MangaEntryRefresher(
    private val updateMangaFromRemote: UpdateMangaFromRemote,
    private val filterChaptersForDownload: FilterChaptersForDownload,
    private val downloadManager: MangaDownloadManager,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val refreshes = InFlightRefreshes<Result<Unit>>(CoroutineScope(SupervisorJob() + dispatcher))

    fun refresh(source: MangaSource, manga: Manga): Deferred<Result<Unit>> =
        refreshes.join(manga.id) {
            updateMangaFromRemote(
                source = source,
                manga = manga,
                fetchDetails = true,
                fetchChapters = true,
                manualFetch = true,
            )
                .onSuccess { downloadNewChapters(it) }
                .map { }
        }

    private suspend fun downloadNewChapters(update: RemoteMangaUpdate) {
        val chapters = filterChaptersForDownload.await(update.manga, update.newChapters)
        if (chapters.isNotEmpty()) downloadManager.downloadChapters(update.manga, chapters)
    }
}
