package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.model.DiscoverCacheSnapshot
import eu.kanade.domain.track.enrichment.model.DiscoverFeedItem
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import tachiyomi.domain.entries.anime.interactor.GetLibraryAnime
import tachiyomi.domain.entries.manga.interactor.GetLibraryManga

class DiscoverFeedCoordinator(
    private val cacheRepository: EnrichmentCacheRepository,
    private val rankingEngine: DiscoverRankingEngine,
    private val bulkEnrichmentCoordinator: BulkEnrichmentCoordinator,
    private val getLibraryManga: GetLibraryManga,
    private val getLibraryAnime: GetLibraryAnime,
) {

    fun observe(limit: Int): Flow<List<DiscoverFeedItem>> {
        return combine(
            cacheRepository.observeDiscoverRecommendations(400),
            cacheRepository.observeDiscoverSnapshots(),
        ) { recommendations, snapshots -> recommendations to snapshots }
            .mapLatest { (recommendations, snapshots) ->
                buildFeed(recommendations, snapshots, limit)
            }
    }

    suspend fun refresh(limit: Int, force: Boolean): List<DiscoverFeedItem> {
        bulkEnrichmentCoordinator.refreshAll(force = force)
        return buildFeed(
            recommendations = cacheRepository.getDiscoverRecommendations(400),
            snapshots = cacheRepository.getDiscoverSnapshots(),
            limit = limit,
        )
    }

    private suspend fun buildFeed(
        recommendations: List<DiscoverRecommendationRecord>,
        snapshots: List<DiscoverCacheSnapshot>,
        limit: Int,
    ): List<DiscoverFeedItem> {
        if (recommendations.isEmpty()) return emptyList()

        val mangaLibrary = getLibraryManga.await()
        val animeLibrary = getLibraryAnime.await()

        val now = System.currentTimeMillis()
        val recentThreshold = now - RECENT_WINDOW_MS
        val seedCompositeScore = snapshots.associateBy({ it.mediaType to it.entryId }, { it.compositeScore ?: 0.0 })

        val seedGenreMap = buildMap {
            mangaLibrary.forEach { item ->
                put(EnrichmentMediaType.MANGA to item.manga.id, item.manga.genre.orEmpty())
            }
            animeLibrary.forEach { item ->
                put(EnrichmentMediaType.ANIME to item.anime.id, item.anime.genre.orEmpty())
            }
        }
        val topGenres = (
            mangaLibrary.flatMap { it.manga.genre.orEmpty() } +
                animeLibrary.flatMap { it.anime.genre.orEmpty() }
            )
            .groupingBy { normalizeGenre(it) }
            .eachCount()
            .filterKeys { it.isNotBlank() }
            .keys

        val recentSeeds = snapshots
            .filter { it.updatedAt >= recentThreshold }
            .map { it.mediaType to it.entryId }
            .toSet()

        val deduped = recommendations
            .filterNot { it.recommendation.inLibrary }
            .groupBy { "${it.mediaType}:${it.recommendation.stableKey}" }
            .map { (_, grouped) ->
                val first = grouped.first()
                val trackers = grouped.flatMap { it.recommendation.trackerSources }.toSet()
                val sourceCount = trackers.size.coerceAtLeast(first.recommendation.sourceCount)
                val baseScore = grouped.maxOfOrNull { it.recommendation.rankScore } ?: 0.0
                val seedKeys = grouped.map { it.mediaType to it.entryId }.toSet()
                val compositeScore = seedKeys.mapNotNull { seedCompositeScore[it] }.ifEmpty { listOf(0.0) }.average()
                val fromRecentSeed = seedKeys.any { recentSeeds.contains(it) }
                val mergedGenres = seedKeys.flatMap { seedGenreMap[it].orEmpty() }.map(::normalizeGenre).toSet()
                val genreOverlap = mergedGenres.intersect(topGenres).size
                val primaryGenre = mergedGenres.firstOrNull { topGenres.contains(it) }

                DiscoverRankingEngine.RankingInput(
                    stableKey = first.recommendation.stableKey,
                    title = first.recommendation.title,
                    mediaType = first.mediaType,
                    targetUrl = first.recommendation.targetUrl,
                    trackerSources = trackers,
                    sourceCount = sourceCount,
                    confidence = grouped.maxOfOrNull {
                        it.recommendation.confidence
                    } ?: first.recommendation.confidence,
                    baseScore = baseScore + sourceCount,
                    compositeScore = compositeScore,
                    fromRecentSeed = fromRecentSeed,
                    genreOverlap = genreOverlap,
                    seedPrimaryGenre = primaryGenre,
                    alternatives = grouped.flatMap { it.recommendation.alternatives }
                        .distinctBy { it.targetUrl to it.trackerSource },
                )
            }

        return rankingEngine.rank(deduped, limit)
    }

    private fun normalizeGenre(value: String): String {
        return value.trim().lowercase()
    }

    private companion object {
        const val RECENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }
}
