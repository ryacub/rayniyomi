package eu.kanade.tachiyomi.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackTickPolicyTest {

    private val policy = PlaybackTickPolicy(saveIntervalMs = 10_000L)

    @Test
    fun `completion is claimed once for repeated ticks of one episode`() {
        val claims = List(5) { policy.claimCompletion(episodeId = 1L) }

        assertEquals(listOf(true, false, false, false, false), claims)
    }

    @Test
    fun `completion is claimed again after another episode completes`() {
        policy.claimCompletion(episodeId = 1L)
        policy.claimCompletion(episodeId = 2L)

        assertTrue(policy.claimCompletion(episodeId = 1L))
    }

    @Test
    fun `download-ahead is claimed once per episode`() {
        val firstEpisode = List(3) { policy.claimDownloadAhead(episodeId = 1L) }
        val secondEpisode = policy.claimDownloadAhead(episodeId = 2L)

        assertEquals(listOf(true, false, false), firstEpisode)
        assertTrue(secondEpisode)
    }

    @Test
    fun `first tick of an episode saves`() {
        assertTrue(policy.claimTickSave(episodeId = 1L, positionMs = 30_000L))
    }

    @Test
    fun `ticks within the interval of the last save do not save`() {
        policy.claimTickSave(episodeId = 1L, positionMs = 0L)

        val saves = (1L..9L).map { policy.claimTickSave(episodeId = 1L, positionMs = it * 1_000L) }

        assertEquals(List(9) { false }, saves)
        assertTrue(policy.claimTickSave(episodeId = 1L, positionMs = 10_000L))
    }

    @Test
    fun `interval counts from the last save, not the last tick`() {
        policy.claimTickSave(episodeId = 1L, positionMs = 0L)
        policy.claimTickSave(episodeId = 1L, positionMs = 6_000L)

        assertFalse(policy.claimTickSave(episodeId = 1L, positionMs = 9_000L))
        assertTrue(policy.claimTickSave(episodeId = 1L, positionMs = 12_000L))
    }

    @Test
    fun `backward seek of the interval saves`() {
        policy.claimTickSave(episodeId = 1L, positionMs = 600_000L)

        assertTrue(policy.claimTickSave(episodeId = 1L, positionMs = 60_000L))
    }

    @Test
    fun `resume after a zero tick saves the resumed position`() {
        assertTrue(policy.claimTickSave(episodeId = 1L, positionMs = 0L))
        assertTrue(policy.claimTickSave(episodeId = 1L, positionMs = 1_300_000L))
    }

    @Test
    fun `new episode saves even at the same position`() {
        policy.claimTickSave(episodeId = 1L, positionMs = 5_000L)

        assertTrue(policy.claimTickSave(episodeId = 2L, positionMs = 5_000L))
    }
}
