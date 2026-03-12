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

        // Single pass: build seedGenreMap with pre-normalized genres + topGenres simultaneously
        val seedGenreMap = HashMap<Pair<EnrichmentMediaType, Long>, List<String>>(mangaLibrary.size + animeLibrary.size)
        val genreFreq = HashMap<String, Int>()
        mangaLibrary.forEach { item ->
            val normalized = item.manga.genre.orEmpty().mapNotNull { g ->
                normalizeGenre(g).takeIf { it.isNotBlank() }
            }
            seedGenreMap[EnrichmentMediaType.MANGA to item.manga.id] = normalized
            normalized.forEach { g -> genreFreq[g] = (genreFreq[g] ?: 0) + 1 }
        }
        animeLibrary.forEach { item ->
            val normalized = item.anime.genre.orEmpty().mapNotNull { g ->
                normalizeGenre(g).takeIf { it.isNotBlank() }
            }
            seedGenreMap[EnrichmentMediaType.ANIME to item.anime.id] = normalized
            normalized.forEach { g -> genreFreq[g] = (genreFreq[g] ?: 0) + 1 }
        }
        val topGenres: Set<String> = genreFreq.keys

        val recentSeeds = snapshots
            .filter { it.updatedAt >= recentThreshold }
            .map { it.mediaType to it.entryId }
            .toSet()

        val deduped = recommendations
            .asSequence()
            .filterNot { it.recommendation.inLibrary }
            .groupBy { it.mediaType to it.recommendation.stableKey }
            .map { (_, grouped) ->
                val first = grouped.first()
                val trackers = mutableSetOf<String>().also { s -> grouped.forEach { s.addAll(it.recommendation.trackerSources) } }
                val sourceCount = trackers.size.coerceAtLeast(first.recommendation.sourceCount)
                val baseScore = grouped.maxOfOrNull { it.recommendation.rankScore } ?: 0.0
                val seedKeys = HashSet<Pair<EnrichmentMediaType, Long>>(grouped.size * 2)
                grouped.forEach { seedKeys.add(it.mediaType to it.entryId) }
                var compSum = 0.0; var compCount = 0
                seedKeys.forEach { k -> seedCompositeScore[k]?.let { v -> compSum += v; compCount++ } }
                val compositeScore = if (compCount > 0) compSum / compCount else 0.0
                val fromRecentSeed = seedKeys.any { recentSeeds.contains(it) }
                // seedGenreMap stores pre-normalized genres — no re-normalization needed
                val mergedGenres = HashSet<String>()
                seedKeys.forEach { key -> seedGenreMap[key]?.forEach { mergedGenres.add(it) } }
                val genreOverlap = mergedGenres.count { topGenres.contains(it) }
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
