package eu.kanade.domain.track.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecommendationAggregatorTest {

    private val aggregator = RecommendationAggregator()

    @Test
    fun `mergeAndRank ranks by source count and marks library entries`() {
        val candidates = listOf(
            RecommendationAggregator.RecommendationCandidate(
                canonicalKey = "https://tracker/a",
                fallbackKey = "fallback:a",
                title = "Alpha",
                targetUrl = "https://tracker/a",
                trackerSource = "AniList",
            ),
            RecommendationAggregator.RecommendationCandidate(
                canonicalKey = "https://tracker/a",
                fallbackKey = "fallback:a",
                title = "Alpha",
                targetUrl = "https://tracker/a",
                trackerSource = "MAL",
            ),
            RecommendationAggregator.RecommendationCandidate(
                canonicalKey = "https://tracker/b",
                fallbackKey = "fallback:b",
                title = "Beta",
                targetUrl = "https://tracker/b",
                trackerSource = "Kitsu",
            ),
        )

        val merged = aggregator.mergeAndRank(candidates) { it.equals("Alpha", ignoreCase = true) }

        assertEquals(2, merged.size)
        assertEquals("Alpha", merged.first().title)
        assertEquals(2, merged.first().sourceCount)
        assertTrue(merged.first().inLibrary)
    }

    @Test
    fun `mergeAndRank keeps alternatives when fallback title collides`() {
        val candidates = listOf(
            RecommendationAggregator.RecommendationCandidate(
                canonicalKey = "https://tracker/a1",
                fallbackKey = "fallback:same",
                title = "Same Title",
                targetUrl = "https://tracker/a1",
                trackerSource = "AniList",
            ),
            RecommendationAggregator.RecommendationCandidate(
                canonicalKey = "https://tracker/b1",
                fallbackKey = "fallback:same",
                title = "Same Title",
                targetUrl = "https://tracker/b1",
                trackerSource = "MAL",
            ),
        )

        val merged = aggregator.mergeAndRank(candidates) { false }

        assertEquals(1, merged.size)
        assertTrue(merged.first().confidence < 0.6)
        assertEquals(2, merged.first().alternatives.size)
    }
}
