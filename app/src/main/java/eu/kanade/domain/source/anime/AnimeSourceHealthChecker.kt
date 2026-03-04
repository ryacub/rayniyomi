package eu.kanade.domain.source.anime

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.health.SourceHealthChecker
import tachiyomi.source.local.entries.anime.LocalAnimeSource

class AnimeSourceHealthChecker(
    private val sourceManager: AnimeSourceManager,
) : SourceHealthChecker {
    override suspend fun probe(sourceId: Long) {
        val source = sourceManager.get(sourceId) as? AnimeCatalogueSource
            ?: throw IllegalStateException("Source $sourceId not available")
        source.getPopularAnime(1)
    }

    override fun shouldSkip(sourceId: Long): Boolean =
        sourceId == LocalAnimeSource.ID ||
            sourceManager.get(sourceId) !is AnimeCatalogueSource
}
