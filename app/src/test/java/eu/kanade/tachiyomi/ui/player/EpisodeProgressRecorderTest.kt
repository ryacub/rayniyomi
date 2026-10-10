package eu.kanade.tachiyomi.ui.player

import eu.kanade.tachiyomi.data.database.models.anime.Episode
import eu.kanade.tachiyomi.data.database.models.anime.EpisodeImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeProgressRecorderTest {

    private val sink = RecordingSink()
    private var tracking = true
    private val recorder = EpisodeProgressRecorder(
        sink = sink,
        completeFraction = { 0.85f },
        shouldTrack = { tracking },
        saveIntervalMs = 10_000L,
    )

    private val episodeOne = EpisodeImpl(1L)
    private val episodeTwo = EpisodeImpl(2L)

    @Test
    fun `ticks past the threshold complete the episode once`() {
        repeat(5) { recorder.onLocalTick(episodeOne, positionMs = 90_000L + it * 1_000L, totalMs = 100_000L) }

        assertEquals(listOf(1L), sink.completed)
    }

    @Test
    fun `completion marks the episode seen before it saves`() {
        recorder.onLocalTick(episodeOne, positionMs = 0L, totalMs = 100_000L)
        sink.saved.clear()

        recorder.onLocalTick(episodeOne, positionMs = 86_000L, totalMs = 100_000L)

        assertEquals(listOf(SavedState(1L, seen = true, positionMs = 86_000L)), sink.saved)
    }

    @Test
    fun `completion tick saves even inside the save interval`() {
        recorder.onLocalTick(episodeOne, positionMs = 84_000L, totalMs = 100_000L)

        recorder.onLocalTick(episodeOne, positionMs = 85_000L, totalMs = 100_000L)

        assertEquals(listOf(84_000L, 85_000L), sink.saved.map { it.positionMs })
    }

    @Test
    fun `no completion and no forced save when tracking is off`() {
        tracking = false
        recorder.onLocalTick(episodeOne, positionMs = 84_000L, totalMs = 100_000L)

        recorder.onLocalTick(episodeOne, positionMs = 90_000L, totalMs = 100_000L)

        assertEquals(emptyList<Long>(), sink.completed)
        assertEquals(listOf(84_000L), sink.saved.map { it.positionMs })
    }

    @Test
    fun `completion runs again after another episode completes`() {
        recorder.onLocalTick(episodeOne, positionMs = 90_000L, totalMs = 100_000L)
        recorder.onLocalTick(episodeTwo, positionMs = 90_000L, totalMs = 100_000L)
        recorder.onLocalTick(episodeOne, positionMs = 91_000L, totalMs = 100_000L)

        assertEquals(listOf(1L, 2L, 1L), sink.completed)
    }

    @Test
    fun `every tick records the position on the episode`() {
        recorder.onLocalTick(episodeOne, positionMs = 0L, totalMs = 100_000L)
        recorder.onLocalTick(episodeOne, positionMs = 3_000L, totalMs = 100_000L)

        assertEquals(3_000L, episodeOne.last_second_seen)
        assertEquals(100_000L, episodeOne.total_seconds)
    }

    @Test
    fun `ticks within the interval of the last save do not save`() {
        (0L..10L).forEach { recorder.onLocalTick(episodeOne, positionMs = it * 1_000L, totalMs = 1_000_000L) }

        assertEquals(listOf(0L, 10_000L), sink.saved.map { it.positionMs })
    }

    @Test
    fun `interval counts from the last save, not the last tick`() {
        listOf(0L, 6_000L, 9_000L, 12_000L).forEach {
            recorder.onLocalTick(episodeOne, positionMs = it, totalMs = 1_000_000L)
        }

        assertEquals(listOf(0L, 12_000L), sink.saved.map { it.positionMs })
    }

    @Test
    fun `backward seek of the interval saves`() {
        recorder.onLocalTick(episodeOne, positionMs = 600_000L, totalMs = 1_000_000L)
        recorder.onLocalTick(episodeOne, positionMs = 60_000L, totalMs = 1_000_000L)

        assertEquals(listOf(600_000L, 60_000L), sink.saved.map { it.positionMs })
    }

    @Test
    fun `new episode saves even at the same position`() {
        recorder.onLocalTick(episodeOne, positionMs = 5_000L, totalMs = 1_000_000L)
        recorder.onLocalTick(episodeTwo, positionMs = 5_000L, totalMs = 1_000_000L)

        assertEquals(listOf(1L, 2L), sink.saved.map { it.episodeId })
    }

    @Test
    fun `download-ahead runs once per episode past 35 percent`() {
        listOf(30_000L, 36_000L, 50_000L, 70_000L).forEach {
            recorder.onLocalTick(episodeOne, positionMs = it, totalMs = 100_000L)
        }
        recorder.onLocalTick(episodeTwo, positionMs = 40_000L, totalMs = 100_000L)

        assertEquals(2, sink.downloadAheadCalls)
    }

    @Test
    fun `no download-ahead below 35 percent`() {
        listOf(0L, 10_000L, 35_000L).forEach {
            recorder.onLocalTick(episodeOne, positionMs = it, totalMs = 100_000L)
        }

        assertEquals(0, sink.downloadAheadCalls)
    }

    @Test
    fun `cast progress saves every update and completes once`() {
        listOf(1_000L, 2_000L, 90_000L, 91_000L).forEach {
            recorder.onCastProgress(episodeOne, positionMs = it, totalMs = 100_000L)
        }

        assertEquals(listOf(1_000L, 2_000L, 90_000L, 91_000L), sink.saved.map { it.positionMs })
        assertEquals(listOf(1L), sink.completed)
        assertEquals(0, sink.downloadAheadCalls)
    }

    @Test
    fun `zero duration records nothing`() {
        recorder.onLocalTick(episodeOne, positionMs = 5_000L, totalMs = 0L)
        recorder.onCastProgress(episodeOne, positionMs = 5_000L, totalMs = 0L)

        assertTrue(sink.saved.isEmpty())
        assertEquals(0L, episodeOne.last_second_seen)
    }

    private data class SavedState(val episodeId: Long, val seen: Boolean, val positionMs: Long)

    private class RecordingSink : EpisodeProgressSink {
        val saved = mutableListOf<SavedState>()
        val completed = mutableListOf<Long>()
        var downloadAheadCalls = 0

        override fun save(episode: Episode) {
            saved += SavedState(episode.id, episode.seen, episode.last_second_seen)
        }

        override fun complete(episode: Episode) {
            completed += episode.id
        }

        override fun downloadAhead() {
            downloadAheadCalls++
        }
    }
}
