package eu.kanade.tachiyomi.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MpvLoadTrackerTest {

    private val tracker = MpvLoadTracker<String>()

    @Test
    fun `latest queued request is reserved in order after the active file starts`() {
        reserveAndIssue("a")
        tracker.onFileStarted()
        tracker.onFileLoaded()

        val b = tracker.requestLoad("b") ?: error("B should reserve immediately")
        assertNull(tracker.requestLoad("c"))
        assertNull(tracker.requestLoad("d"))
        assertTrue(tracker.onLoadIssued(b))

        val d = tracker.onFileStarted() ?: error("D should reserve after B starts")
        assertEquals("d", d.item)
        assertNull(tracker.requestLoad("e"))
        assertTrue(tracker.onLoadIssued(d))
        assertEquals("e", tracker.onFileStarted()?.item)
    }

    @Test
    fun `old error and file loaded events do not consume queued start`() {
        reserveAndIssue("a")
        tracker.onFileStarted()
        tracker.onFileLoaded()
        reserveAndIssue("b")
        assertNull(tracker.requestLoad("c"))

        val c = tracker.onFileStarted() ?: error("C should reserve after B starts")
        assertEquals("c", c.item)
        assertNull(tracker.onLoadError())

        // B can finish loading after C is reserved but before C is issued or starts.
        tracker.onFileLoaded()
        assertTrue(tracker.onLoadIssued(c))
        assertNull(tracker.onLoadError())

        tracker.onFileStarted()
        assertEquals("c", tracker.onLoadError())
    }

    @Test
    fun `close cancels reserved and queued requests`() {
        reserveAndIssue("a")
        assertNull(tracker.requestLoad("b"))
        val b = tracker.onFileStarted()
        assertNotNull(b)

        tracker.close()

        assertFalse(tracker.onLoadIssued(b!!))
        assertNull(tracker.onFileStarted())
        assertNull(tracker.requestLoad("c"))
        assertNull(tracker.onLoadError())
    }

    @Test
    fun `load error reports latest started request once`() {
        val a = reserveAndIssue("a")
        assertNull(tracker.onLoadError())
        tracker.onFileStarted()

        assertEquals("a", tracker.onLoadError())
        assertNull(tracker.onLoadError())
        assertEquals("a", a.item)
    }

    @Test
    fun `fallback load completion clears failed start state`() {
        reserveAndIssue("b")
        tracker.onFileStarted()
        assertEquals("b", tracker.onLoadError())

        reserveAndIssue("c")
        tracker.onFileStarted()
        tracker.onFileLoaded()

        assertNull(tracker.onLoadError())
    }

    private fun reserveAndIssue(item: String): MpvLoadTracker.Reservation<String> {
        val reservation = tracker.requestLoad(item) ?: error("$item should reserve immediately")
        assertTrue(tracker.onLoadIssued(reservation))
        return reservation
    }
}
