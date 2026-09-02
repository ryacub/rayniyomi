package eu.kanade.tachiyomi.ui.player.cast

import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.data.database.models.anime.EpisodeImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CastQueuePlannerTest {

    private fun ep(id: Long, seen: Boolean = false): Episode =
        EpisodeImpl(id).apply {
            this.seen = seen
            url = "episode-$id"
            name = "Episode $id"
        }

    @Test
    fun `planLookahead returns the next unseen episodes after the current one`() {
        val playlist = listOf(ep(1), ep(2), ep(3))
        val result = CastQueuePlanner.planLookahead(playlist, currentEpisodeId = 1L)
        assertEquals(listOf(2L, 3L), result.map { it.id })
    }

    @Test
    fun `planLookahead skips episodes already marked seen`() {
        val playlist = listOf(ep(1), ep(2, seen = true), ep(3), ep(4))
        val result = CastQueuePlanner.planLookahead(playlist, currentEpisodeId = 1L)
        assertEquals(listOf(3L, 4L), result.map { it.id })
    }

    @Test
    fun `planLookahead never includes episodes before the current one`() {
        val playlist = listOf(ep(1), ep(2), ep(3))
        val result = CastQueuePlanner.planLookahead(playlist, currentEpisodeId = 2L)
        assertEquals(listOf(3L), result.map { it.id })
    }

    @Test
    fun `planLookahead caps the window at the lookahead constant`() {
        val playlist = (1L..10L).map { ep(it) }
        val result = CastQueuePlanner.planLookahead(playlist, currentEpisodeId = 1L)
        assertEquals(CAST_QUEUE_LOOKAHEAD, result.size)
    }

    @Test
    fun `planLookahead returns empty when the current episode is last`() {
        val playlist = listOf(ep(1), ep(2))
        val result = CastQueuePlanner.planLookahead(playlist, currentEpisodeId = 2L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `planLookahead returns empty when the current episode is not in the playlist`() {
        val playlist = listOf(ep(1), ep(2))
        val result = CastQueuePlanner.planLookahead(playlist, currentEpisodeId = 99L)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `planLookahead keeps playlist order`() {
        val playlist = listOf(ep(1), ep(2), ep(3), ep(4))
        val result = CastQueuePlanner.planLookahead(playlist, currentEpisodeId = 1L, lookahead = 10)
        assertEquals(listOf(2L, 3L, 4L), result.map { it.id })
    }

    @Test
    fun `staleEpisodeIds reports queued episodes that were marked seen`() {
        val queue = listOf(2L, 3L, 4L)
        val playlist = listOf(ep(1), ep(2, seen = true), ep(3), ep(4, seen = true))
        val stale = CastQueuePlanner.staleEpisodeIds(queue, playlist, currentEpisodeId = 1L)
        assertEquals(setOf(2L, 4L), stale.toSet())
    }

    @Test
    fun `staleEpisodeIds never reports the currently playing episode`() {
        val queue = listOf(1L, 2L)
        val playlist = listOf(ep(1, seen = true), ep(2))
        val stale = CastQueuePlanner.staleEpisodeIds(queue, playlist, currentEpisodeId = 1L)
        assertTrue(stale.isEmpty())
    }
}
