package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.model.AggregatedRecommendation
import eu.kanade.domain.track.enrichment.model.RecommendationChoice

class RecommendationAggregator {

    companion object {
        // Confidence scoring model:
        // - direct URL/ID matches from trackers are strongest signals,
        // - fallback title-key matches are weaker heuristic matches,
        // - merged fallback collisions are lowered due to ambiguity.
        private const val CONFIDENCE_URL_MATCH = 0.95
        private const val CONFIDENCE_KEY_MATCH = 0.55
        private const val CONFIDENCE_MERGED_FALLBACK = 0.45

        // Penalize merged collisions where multiple candidates collapse to the same normalized title.
        // Diversity penalty lowers ordering weight for ambiguous buckets.
        private const val DIVERSITY_PENALTY_MULTIPLIER = 0.35

        // Confidence penalty scales down merged candidates so exact canonical matches still outrank them.
        private const val CONFIDENCE_PENALTY = 0.7
    }

    data class RecommendationCandidate(
        val canonicalKey: String,
        val fallbackKey: String,
        val title: String,
        val targetUrl: String?,
        val trackerSource: String,
    )

    fun mergeAndRank(
        items: List<RecommendationCandidate>,
        inLibraryTitleMatcher: (String) -> Boolean,
    ): List<AggregatedRecommendation> {
        if (items.isEmpty()) return emptyList()

        val groupedByCanonical = items.groupBy { it.canonicalKey }
        val canonicalMerged = groupedByCanonical.map { (_, values) ->
            val first = values.first()
            val sources = values.map { it.trackerSource }.distinct()
            val alternatives = values.distinctBy { it.targetUrl to it.trackerSource }.map {
                RecommendationChoice(
                    title = it.title,
                    targetUrl = it.targetUrl,
                    trackerSource = it.trackerSource,
                )
            }
            AggregatedRecommendation(
                stableKey = first.canonicalKey,
                title = first.title,
                targetUrl = first.targetUrl,
                trackerSources = sources,
                sourceCount = sources.size,
                confidence = if (first.canonicalKey.startsWith("fallback:")) {
                    CONFIDENCE_KEY_MATCH
                } else {
                    CONFIDENCE_URL_MATCH
                },
                inLibrary = inLibraryTitleMatcher(first.title),
                rankScore = sources.size.toDouble(),
                alternatives = alternatives,
            )
        }

        val fallbackBuckets = canonicalMerged.groupBy { normalizeTitleKey(it.title) }
        val resolved = mutableListOf<AggregatedRecommendation>()

        fallbackBuckets.values.forEach { bucket ->
            if (bucket.size == 1) {
                resolved += bucket.first()
            } else {
                val first = bucket.maxByOrNull { it.sourceCount } ?: return@forEach
                val allSources = bucket.flatMap { it.trackerSources }.distinct()
                val alternatives = bucket.flatMap { rec ->
                    if (rec.alternatives.isEmpty()) {
                        listOf(
                            RecommendationChoice(
                                title = rec.title,
                                targetUrl = rec.targetUrl,
                                trackerSource = rec.trackerSources.joinToString(", "),
                            ),
                        )
                    } else {
                        rec.alternatives
                    }
                }.distinctBy { it.targetUrl to it.trackerSource }
                val diversityPenalty = (bucket.size - 1) * DIVERSITY_PENALTY_MULTIPLIER
                resolved += first.copy(
                    trackerSources = allSources,
                    sourceCount = allSources.size,
                    confidence = (first.confidence * CONFIDENCE_PENALTY)
                        .coerceAtMost(CONFIDENCE_KEY_MATCH)
                        .coerceAtLeast(CONFIDENCE_MERGED_FALLBACK),
                    rankScore = allSources.size - diversityPenalty,
                    alternatives = alternatives,
                )
            }
        }

        return resolved
            .sortedWith(
                compareByDescending<AggregatedRecommendation> { it.sourceCount }
                    .thenByDescending { it.rankScore }
                    .thenBy { it.title.lowercase() },
            )
    }

    fun dedupe(items: List<RecommendationCandidate>): List<RecommendationCandidate> {
        return items.distinctBy { it.canonicalKey }
    }

    fun normalizeTitleKey(title: String): String {
        return title
            .lowercase()
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()
    }
}
