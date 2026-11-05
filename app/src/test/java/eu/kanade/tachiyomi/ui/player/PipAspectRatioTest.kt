package eu.kanade.tachiyomi.ui.player

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PipAspectRatioTest {

    @Test
    fun `height without a video aspect gives no aspect ratio`() {
        assertNull(pipAspectRatio(videoHeight = 1080, videoAspect = null))
    }

    @Test
    fun `height with a landscape video aspect gives an aspect ratio`() {
        assertNotNull(pipAspectRatio(videoHeight = 1080, videoAspect = 16.0 / 9.0))
    }

    @Test
    fun `zero video aspect gives no aspect ratio`() {
        assertNull(pipAspectRatio(videoHeight = 1080, videoAspect = 0.0))
    }
}
