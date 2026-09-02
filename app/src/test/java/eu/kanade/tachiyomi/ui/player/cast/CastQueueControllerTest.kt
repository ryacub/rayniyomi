package eu.kanade.tachiyomi.ui.player.cast

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CastQueueControllerTest {

    private val events = mutableListOf<String>()
    private val sink = object : CastQueueSink {
        override fun onProgress(episodeId: Long, positionMs: Long, durationMs: Long) {
            events += "progress($episodeId,$positionMs,$durationMs)"
        }

        override fun onCurrentEpisodeChanged(episodeId: Long) {
            events += "current($episodeId)"
        }

        override fun onQueueExhausted() {
            events += "exhausted"
        }
    }
    private lateinit var controller: CastQueueController

    @BeforeEach
    fun setup() {
        events.clear()
        controller = CastQueueController(sink)
    }

    private fun snapshot(
        contentId: String? = null,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        isIdleFinished: Boolean = false,
    ) = CastReceiverSnapshot(contentId, positionMs, durationMs, isIdleFinished)

    // ---- Progress reporting ----

    @Test
    fun `onSnapshot reports progress for the episode owning the current contentId`() {
        controller.registerQueuedItem("episode-1", 1L)
        controller.onSnapshot(snapshot(contentId = "episode-1", positionMs = 5000L, durationMs = 120000L))
        assertEquals(listOf("progress(1,5000,120000)"), events)
    }

    @Test
    fun `onSnapshot ignores snapshots with an unknown contentId`() {
        // An unknown contentId is not registered to any episode, so no sink call fires.
        // The controller must record the snapshot anyway so later transitions can finalize it.
        controller.onSnapshot(snapshot(contentId = "unknown", positionMs = 5000L, durationMs = 120000L))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `onSnapshot ignores a zero duration`() {
        controller.registerQueuedItem("episode-1", 1L)
        controller.onSnapshot(snapshot(contentId = "episode-1", positionMs = 5000L, durationMs = 0L))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `onSessionEnded reports the last known position for the current episode`() {
        controller.registerQueuedItem("episode-1", 1L)
        controller.onSnapshot(snapshot(contentId = "episode-1", positionMs = 30000L, durationMs = 120000L))
        events.clear()
        controller.onSessionEnded()
        assertEquals(listOf("progress(1,30000,120000)"), events)
    }

    @Test
    fun `onSessionEnded emits nothing when no item ever played`() {
        controller.onSessionEnded()
        assertTrue(events.isEmpty())
    }

    // ---- Receiver-driven advance ----

    @Test
    fun `a new contentId finalizes the previous episode at full duration`() {
        controller.registerQueuedItem("episode-1", 1L)
        controller.registerQueuedItem("episode-2", 2L)
        controller.onSnapshot(snapshot(contentId = "episode-1", positionMs = 110000L, durationMs = 120000L))
        events.clear()
        controller.onSnapshot(snapshot(contentId = "episode-2", positionMs = 1000L, durationMs = 120000L))
        assertEquals(
            listOf("progress(1,120000,120000)", "current(2)", "progress(2,1000,120000)"),
            events,
        )
    }

    @Test
    fun `a new contentId adopts the new episode as current`() {
        controller.registerQueuedItem("episode-1", 1L)
        controller.registerQueuedItem("episode-2", 2L)
        controller.onSnapshot(snapshot(contentId = "episode-1", positionMs = 0L, durationMs = 120000L))
        events.clear()
        controller.onSnapshot(snapshot(contentId = "episode-2", positionMs = 0L, durationMs = 120000L))
        assertTrue(events.any { it == "current(2)" })
    }

    @Test
    fun `repeat snapshots for the same contentId do not re-emit onCurrentEpisodeChanged`() {
        controller.registerQueuedItem("episode-1", 1L)
        controller.onSnapshot(snapshot(contentId = "episode-1", positionMs = 0L, durationMs = 120000L))
        events.clear()
        controller.onSnapshot(snapshot(contentId = "episode-1", positionMs = 1000L, durationMs = 120000L))
        assertTrue(events.none { it.startsWith("current(") })
        assertEquals(listOf("progress(1,1000,120000)"), events)
    }

    @Test
    fun `an unregistered contentId does not change the current episode`() {
        controller.registerQueuedItem("episode-1", 1L)
        controller.onSnapshot(snapshot(contentId = "episode-1", positionMs = 0L, durationMs = 120000L))
        events.clear()
        controller.onSnapshot(snapshot(contentId = "other", positionMs = 0L, durationMs = 120000L))
        assertTrue(events.none { it.startsWith("current(") })
    }

    @Test
    fun `idle finished finalizes the current episode and reports the queue exhausted`() {
        controller.registerQueuedItem("episode-1", 1L)
        controller.onSnapshot(snapshot(contentId = "episode-1", positionMs = 119000L, durationMs = 120000L))
        events.clear()
        // positionMs is strictly below durationMs so only the full-duration finalize can report 120000.
        controller.onSnapshot(
            snapshot(contentId = "episode-1", positionMs = 60000L, durationMs = 120000L, isIdleFinished = true),
        )
        assertEquals(
            listOf("progress(1,60000,120000)", "progress(1,120000,120000)", "exhausted"),
            events,
        )
    }

    @Test
    fun `reset clears the contentId mapping`() {
        controller.registerQueuedItem("episode-1", 1L)
        controller.reset()
        controller.onSnapshot(snapshot(contentId = "episode-1", positionMs = 5000L, durationMs = 120000L))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `unregisterEpisode drops the episode from queuedEpisodeIds`() {
        controller.registerQueuedItem("episode-1", 1L)
        controller.registerQueuedItem("episode-2", 2L)
        controller.unregisterEpisode(1L)
        assertEquals(listOf(2L), controller.queuedEpisodeIds())
    }
}
