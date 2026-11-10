package eu.kanade.presentation.reader

import androidx.compose.ui.unit.IntSize
import eu.kanade.tachiyomi.ui.reader.CoverHandoff
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CoverMotionTest {

    private val screen = IntSize(412, 915)
    private val phoneCover = CoverHandoff(16f, 104f, 100f, 150f, "https://example.test/cover.jpg")

    @Test
    fun `the move takes the share of the curve that its distance takes of the whole path`() {
        assertEquals(0.430f, coverMoveShare(phoneCover, screen), 0.001f)
    }

    @Test
    fun `the expansion stays still until the move reaches the centre`() {
        val share = coverMoveShare(phoneCover, screen)
        for (step in 0..100) {
            val phases = coverMotionPhases(step / 100f, share)
            if (phases.move < 1f) assertEquals(0f, phases.expand, "progress ${step / 100f}")
        }
    }

    @Test
    fun `both phases finish together at the end of the curve`() {
        val phases = coverMotionPhases(1f, coverMoveShare(phoneCover, screen))

        assertEquals(1f, phases.move)
        assertEquals(1f, phases.expand)
    }

    @Test
    fun `a cover already at the centre only expands`() {
        val centred = CoverHandoff(156f, 382.5f, 100f, 150f, "https://example.test/cover.jpg")
        val share = coverMoveShare(centred, screen)

        val phases = coverMotionPhases(0.5f, share)

        assertEquals(1f, phases.move)
        assertEquals(0.5f, phases.expand, 0.001f)
    }
}
