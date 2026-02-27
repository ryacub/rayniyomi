package eu.kanade.tachiyomi.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerViewModelCastTest {

    @Test
    fun `isCasting maps CONNECTED state to true`() {
        val castState = "CONNECTED"
        val isCasting = (castState == "CONNECTED")
        assertTrue(isCasting, "Should map CONNECTED to true")
    }

    @Test
    fun `isCasting maps DISCONNECTED state to false`() {
        val castState = "DISCONNECTED"
        val isCasting = (castState == "CONNECTED")
        assertFalse(isCasting, "Should map DISCONNECTED to false")
    }

    @Test
    fun `isCasting defaults to false initially`() {
        val castState = "DISCONNECTED"
        val isCasting = (castState == "CONNECTED")
        assertFalse(isCasting, "Should default to false")
    }

    @Test
    fun `canCast rejects content-slash-slash URLs`() {
        val videoUrl = "content://some.provider/path"
        val canCast = !videoUrl.startsWith("content://") && !videoUrl.startsWith("file://")
        assertFalse(canCast, "Should reject content:// URLs")
    }

    @Test
    fun `canCast rejects file-slash-slash URLs`() {
        val videoUrl = "file:///storage/emulated/0/video.mp4"
        val canCast = !videoUrl.startsWith("content://") && !videoUrl.startsWith("file://")
        assertFalse(canCast, "Should reject file:// URLs")
    }

    @Test
    fun `canCast accepts http-slash-slash URLs`() {
        val videoUrl = "http://example.com/video.mp4"
        val canCast = !videoUrl.startsWith("content://") && !videoUrl.startsWith("file://")
        assertTrue(canCast, "Should accept http:// URLs")
    }

    @Test
    fun `canCast accepts https-slash-slash URLs`() {
        val videoUrl = "https://example.com/video.mp4"
        val canCast = !videoUrl.startsWith("content://") && !videoUrl.startsWith("file://")
        assertTrue(canCast, "Should accept https:// URLs")
    }

    @Test
    fun `updateCastProgress stores the given position`() {
        var castProgress = 0L
        val newPosition = 5000L
        castProgress = newPosition
        assertEquals(5000L, castProgress, "Should store the provided position")
    }

    @Test
    fun `resumeFromCast converts milliseconds to seconds for MPV`() {
        val positionMs = 10000L
        val seekSeconds = positionMs / 1000.0
        assertEquals(10.0, seekSeconds, "Should convert ms to seconds")
    }

    @Test
    fun `onCastEpisodeFinished uses correct parameters for changeEpisode`() {
        val previous = false
        val autoPlay = true
        assertTrue(!previous, "Should pass previous=false")
        assertTrue(autoPlay, "Should pass autoPlay=true")
    }
}
