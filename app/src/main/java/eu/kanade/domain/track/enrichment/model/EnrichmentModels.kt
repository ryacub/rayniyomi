package eu.kanade.domain.track.enrichment.model

import kotlinx.serialization.Serializable

@Serializable
enum class EnrichmentMediaType {
    MANGA,
    ANIME,
}

@Serializable
data class EnrichmentFailure(
    val trackerId: Long,
    val trackerName: String,
    val category: String,
    val userMessage: String,
    val retriable: Boolean,
)

@Serializable
data class AggregatedRecommendation(
    val stableKey: String,
    val title: String,
    val targetUrl: String?,
    val trackerSources: List<String>,
    val sourceCount: Int,
    val confidence: Double,
    val inLibrary: Boolean,
    val rankScore: Double,
    val alternatives: List<RecommendationChoice> = emptyList(),
)

@Serializable
data class RecommendationChoice(
    val title: String,
    val targetUrl: String?,
    val trackerSource: String,
)

@Serializable
data class EnrichedEntry(
    val entryId: Long,
    val mediaType: EnrichmentMediaType,
    val mergedTitle: String,
    val compositeScore: Double?,
    val confidenceLabel: String,
    val sourceCoverage: List<String>,
    val summary: String,
    val recommendations: List<AggregatedRecommendation>,
    val failures: List<EnrichmentFailure>,
    val updatedAt: Long,
    val expiresAt: Long,
)

enum class DiscoverReasonKind {
    MULTI_TRACKER,
    HIGH_SCORE_SEED,
    RECENT_ACTIVITY,
    GENRE_MATCH,
    LOW_CONFIDENCE,
}

data class DiscoverReason(
    val kind: DiscoverReasonKind,
    val label: String,
)

data class DiscoverFeedItem(
    val stableKey: String,
    val title: String,
    val mediaType: EnrichmentMediaType,
    val targetUrl: String?,
    val trackerSources: List<String>,
    val sourceCount: Int,
    val confidence: Double,
    val score: Double,
    val reason: DiscoverReason,
    val alternatives: List<RecommendationChoice> = emptyList(),
)

data class DiscoverCacheSnapshot(
    val entryId: Long,
    val mediaType: EnrichmentMediaType,
    val updatedAt: Long,
    val expiresAt: Long,
    val compositeScore: Double?,
)
