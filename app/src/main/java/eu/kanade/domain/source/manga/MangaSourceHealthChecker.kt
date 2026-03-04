package eu.kanade.domain.source.manga

import eu.kanade.tachiyomi.source.CatalogueSource
import tachiyomi.domain.source.health.SourceHealthChecker
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.source.local.entries.manga.LocalMangaSource

class MangaSourceHealthChecker(
    private val sourceManager: MangaSourceManager,
) : SourceHealthChecker {
    override suspend fun probe(sourceId: Long) {
        val source = sourceManager.get(sourceId) as? CatalogueSource
            ?: throw IllegalStateException("Source $sourceId not available")
        source.getPopularManga(1)
    }

    override fun shouldSkip(sourceId: Long): Boolean =
        sourceId == LocalMangaSource.ID ||
            sourceManager.get(sourceId) !is CatalogueSource
}
