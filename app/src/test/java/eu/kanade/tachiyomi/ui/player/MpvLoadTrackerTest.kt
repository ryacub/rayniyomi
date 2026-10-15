package eu.kanade.tachiyomi.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class MpvLoadTrackerTest {

    private val tracker = MpvLoadTracker<String>()

    @Test
    fun `an error while the latest file opens names that file`() {
        tracker.onLoadIssued("a")
        tracker.onFileStarted()

        assertEquals("a", tracker.onLoadError())
    }

    @Test
    fun `an error is reported once`() {
        tracker.onLoadIssued("a")
        tracker.onFileStarted()
        tracker.onLoadError()

        assertNull(tracker.onLoadError())
    }

    @Test
    fun `an error after the file loaded is not a load failure`() {
        tracker.onLoadIssued("a")
        tracker.onFileStarted()
        tracker.onFileLoaded()

        assertNull(tracker.onLoadError())
    }

    @Test
    fun `an error from an older file after a manual switch is ignored`() {
        tracker.onLoadIssued("a")
        tracker.onFileStarted()
        tracker.onLoadIssued("b")

        assertNull(tracker.onLoadError())
    }

    @Test
    fun `an older file that started late does not take the newer file's error`() {
        tracker.onLoadIssued("a")
        tracker.onLoadIssued("b")
        tracker.onFileStarted()

        assertNull(tracker.onLoadError())

        tracker.onFileStarted()
        assertEquals("b", tracker.onLoadError())
    }

    @Test
    fun `the newer file fails after an older file loaded late`() {
        tracker.onLoadIssued("a")
        tracker.onFileStarted()
        tracker.onLoadIssued("b")
        tracker.onFileLoaded()
        tracker.onFileStarted()

        assertEquals("b", tracker.onLoadError())
    }

    @Test
    fun `an error with no load issued is ignored`() {
        tracker.onFileStarted()

        assertNull(tracker.onLoadError())
    }
}
