package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.interactor.ComputeCompositeScore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComputeCompositeScoreTest {

    private val computeCompositeScore = ComputeCompositeScore()

    @Test
    fun `returns average for positive scores`() {
        val value = computeCompositeScore(listOf(8.0, 6.0, 10.0))
        assertEquals(8.0, value)
    }

    @Test
    fun `returns null when no valid scores`() {
        val value = computeCompositeScore(listOf(0.0, -1.0))
        assertNull(value)
    }
}
