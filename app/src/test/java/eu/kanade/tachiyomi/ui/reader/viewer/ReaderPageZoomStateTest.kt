package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderPageZoomStateTest {

    @Test
    fun `scale equal to minScale is at minimum zoom`() {
        assertTrue(isAtMinimumZoom(scale = 1.0f, minScale = 1.0f))
    }

    @Test
    fun `scale below minScale is at minimum zoom`() {
        assertTrue(isAtMinimumZoom(scale = 0.9f, minScale = 1.0f))
    }

    @Test
    fun `scale above minScale is not at minimum zoom`() {
        assertFalse(isAtMinimumZoom(scale = 1.5f, minScale = 1.0f))
    }

    @Test
    fun `a float rounding difference still counts as minimum zoom`() {
        assertTrue(isAtMinimumZoom(scale = 1.0000001f, minScale = 1.0f))
    }
}
