package eu.kanade.tachiyomi.ui.player

import android.content.pm.ActivityInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerOrientationMappingTest {

    @Test
    fun `reverse landscape maps to the reverse landscape activity constant`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            PlayerOrientation.ReverseLandscape.toActivityOrientation(videoAspect = null),
        )
    }

    @Test
    fun `reverse portrait and reverse landscape do not share a constant`() {
        val reversePortrait = PlayerOrientation.ReversePortrait.toActivityOrientation(videoAspect = null)
        val reverseLandscape = PlayerOrientation.ReverseLandscape.toActivityOrientation(videoAspect = null)
        assertEquals(false, reversePortrait == reverseLandscape)
    }

    @Test
    fun `every fixed orientation maps to its matching activity constant`() {
        val expected = mapOf(
            PlayerOrientation.Free to ActivityInfo.SCREEN_ORIENTATION_SENSOR,
            PlayerOrientation.Portrait to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            PlayerOrientation.ReversePortrait to ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            PlayerOrientation.SensorPortrait to ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            PlayerOrientation.Landscape to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            PlayerOrientation.ReverseLandscape to ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
            PlayerOrientation.SensorLandscape to ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        )

        expected.forEach { (orientation, constant) ->
            assertEquals(constant, orientation.toActivityOrientation(videoAspect = null), orientation.name)
        }
    }

    @Test
    fun `video orientation follows the video aspect ratio`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            PlayerOrientation.Video.toActivityOrientation(videoAspect = 1.78),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            PlayerOrientation.Video.toActivityOrientation(videoAspect = 0.56),
        )
    }

    @Test
    fun `video orientation falls back to portrait when the aspect is unknown`() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            PlayerOrientation.Video.toActivityOrientation(videoAspect = null),
        )
    }

    @Test
    fun `every orientation value is covered by the mapping`() {
        PlayerOrientation.entries.forEach { orientation ->
            assertEquals(
                true,
                orientation.toActivityOrientation(videoAspect = null) != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                orientation.name,
            )
        }
    }
}
