package eu.kanade.tachiyomi.util

import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.domain.entries.manga.model.toSManga
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.source.local.entries.manga.isLocal
import tachiyomi.source.local.image.manga.LocalMangaCoverManager
import java.io.InputStream
import java.time.Instant

fun Manga.removeCovers(coverCache: MangaCoverCache = appGraph.mangaCoverCache): Manga {
    if (isLocal()) return this
    return if (coverCache.deleteFromCache(this, true) > 0) {
        return copy(coverLastModified = Instant.now().toEpochMilli())
    } else {
        this
    }
}

suspend fun Manga.editCover(
    coverManager: LocalMangaCoverManager,
    stream: InputStream,
    updateManga: UpdateManga = appGraph.updateManga,
    coverCache: MangaCoverCache = appGraph.mangaCoverCache,
) {
    if (isLocal()) {
        coverManager.update(toSManga(), stream)
        updateManga.awaitUpdateCoverLastModified(id)
    } else if (favorite) {
        coverCache.setCustomCoverToCache(this, stream)
        updateManga.awaitUpdateCoverLastModified(id)
    }
}
