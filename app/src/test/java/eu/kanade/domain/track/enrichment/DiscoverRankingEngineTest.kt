package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.model.DiscoverReasonKind
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscoverRankingEngineTest {

    private val engine = DiscoverRankingEngine()

    @Test
    fun `rank prefers diverse genre seeds over repeated cluster`() {
        val inputs = listOf(
            DiscoverRankingEngine.RankingInput(
                stableKey = "a",
                title = "A",
                mediaType = EnrichmentMediaType.MANGA,
                targetUrl = "https://a",
                trackerSources = setOf("AniList", "MAL"),
                sourceCount = 2,
                confidence = 0.9,
                baseScore = 4.0,
                compositeScore = 8.0,
                fromRecentSeed = true,
                genreOverlap = 2,
                seedPrimaryGenre = "action",
                alternatives = emptyList(),
            ),
            DiscoverRankingEngine.RankingInput(
                stableKey = "b",
                title = "B",
                mediaType = EnrichmentMediaType.MANGA,
                targetUrl = "https://b",
                trackerSources = setOf("AniList"),
                sourceCount = 1,
                confidence = 0.8,
                baseScore = 3.7,
                compositeScore = 8.0,
                fromRecentSeed = true,
                genreOverlap = 2,
                seedPrimaryGenre = "action",
                alternatives = emptyList(),
            ),
            DiscoverRankingEngine.RankingInput(
                stableKey = "c",
                title = "C",
                mediaType = EnrichmentMediaType.MANGA,
                targetUrl = "https://c",
                trackerSources = setOf("Kitsu"),
                sourceCount = 1,
                confidence = 0.75,
                baseScore = 3.5,
                compositeScore = 8.0,
                fromRecentSeed = true,
                genreOverlap = 2,
                seedPrimaryGenre = "mystery",
                alternatives = emptyList(),
            ),
        )

        val ranked = engine.rank(inputs, limit = 3)

        assertEquals("A", ranked[0].title)
        assertEquals("C", ranked[1].title)
        assertEquals("B", ranked[2].title)
    }

    @Test
    fun `rank marks low confidence entries with low confidence reason`() {
        val ranked = engine.rank(
            listOf(
                DiscoverRankingEngine.RankingInput(
                    stableKey = "low",
                    title = "Low Confidence",
                    mediaType = EnrichmentMediaType.ANIME,
                    targetUrl = null,
                    trackerSources = setOf("AniList"),
                    sourceCount = 1,
                    confidence = 0.4,
                    baseScore = 1.0,
                    compositeScore = 0.0,
                    fromRecentSeed = false,
                    genreOverlap = 0,
                    seedPrimaryGenre = null,
                    alternatives = emptyList(),
                ),
            ),
            limit = 1,
        )

        assertEquals(1, ranked.size)
        assertEquals(DiscoverReasonKind.LOW_CONFIDENCE, ranked.first().reason.kind)
        assertTrue(ranked.first().score < 1.0)
    }
}
