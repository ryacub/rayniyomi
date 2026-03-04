package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.model.DiscoverFeedItem
import eu.kanade.domain.track.enrichment.model.DiscoverReason
import eu.kanade.domain.track.enrichment.model.DiscoverReasonKind
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import eu.kanade.domain.track.enrichment.model.RecommendationChoice

class DiscoverRankingEngine {

    data class RankingInput(
        val stableKey: String,
        val title: String,
        val mediaType: EnrichmentMediaType,
        val targetUrl: String?,
        val trackerSources: Set<String>,
        val sourceCount: Int,
        val confidence: Double,
        val baseScore: Double,
        val compositeScore: Double,
        val fromRecentSeed: Boolean,
        val genreOverlap: Int,
        val seedPrimaryGenre: String?,
        val alternatives: List<RecommendationChoice>,
    )

    fun rank(inputs: List<RankingInput>, limit: Int): List<DiscoverFeedItem> {
        if (inputs.isEmpty()) return emptyList()

        val genreCounts = mutableMapOf<String, Int>()
        val remaining = inputs.toMutableList()
        val ranked = mutableListOf<DiscoverFeedItem>()

        while (remaining.isNotEmpty() && ranked.size < limit) {
            val next = remaining.maxByOrNull { input ->
                val diversityPenalty = input.seedPrimaryGenre?.let { genre ->
                    (genreCounts[genre] ?: 0) * 0.35
                } ?: 0.0
                computeWeightedScore(input) - diversityPenalty
            } ?: break

            val reason = selectReason(next)
            val score = computeWeightedScore(next)
            ranked += DiscoverFeedItem(
                stableKey = next.stableKey,
                title = next.title,
                mediaType = next.mediaType,
                targetUrl = next.targetUrl,
                trackerSources = next.trackerSources.toList().sorted(),
                sourceCount = next.sourceCount,
                confidence = next.confidence,
                score = score,
                reason = reason,
                alternatives = next.alternatives,
            )

            next.seedPrimaryGenre?.let { genre ->
                genreCounts[genre] = (genreCounts[genre] ?: 0) + 1
            }
            remaining.remove(next)
        }

        return ranked
    }

    private fun computeWeightedScore(input: RankingInput): Double {
        val relevance = (input.compositeScore.coerceIn(0.0, 10.0) / 10.0) * 1.5
        val recency = if (input.fromRecentSeed) 0.8 else 0.0
        val genre = input.genreOverlap * 0.25
        val confidencePenalty = if (input.confidence < 0.6) 0.7 else 0.0
        return input.baseScore + relevance + recency + genre - confidencePenalty
    }

    private fun selectReason(input: RankingInput): DiscoverReason {
        return when {
            input.genreOverlap >= 2 -> DiscoverReason(
                kind = DiscoverReasonKind.GENRE_MATCH,
                label = "Popular in genres you follow",
            )
            input.fromRecentSeed -> DiscoverReason(
                kind = DiscoverReasonKind.RECENT_ACTIVITY,
                label = "Based on your recent activity",
            )
            input.compositeScore >= 7.0 -> DiscoverReason(
                kind = DiscoverReasonKind.HIGH_SCORE_SEED,
                label = "Because you rated similar titles highly",
            )
            input.sourceCount >= 2 -> DiscoverReason(
                kind = DiscoverReasonKind.MULTI_TRACKER,
                label = "Recommended by multiple trackers",
            )
            else -> DiscoverReason(
                kind = DiscoverReasonKind.LOW_CONFIDENCE,
                label = "Potential match from tracker activity",
            )
        }
    }
}
