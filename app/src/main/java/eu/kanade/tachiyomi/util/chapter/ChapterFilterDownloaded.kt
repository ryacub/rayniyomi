package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.source.local.entries.manga.isLocal

/**
 * Returns a copy of the list with not downloaded chapters removed.
 */
fun List<Chapter>.filterDownloadedChapters(manga: Manga): List<Chapter> {
    if (manga.isLocal()) return this

    val downloadCache: MangaDownloadCache = appGraph.mangaDownloadCache

    return filter {
        downloadCache.isChapterDownloaded(
            it.name,
            it.scanlator,
            manga.title,
            manga.source,
            false,
        )
    }
}
