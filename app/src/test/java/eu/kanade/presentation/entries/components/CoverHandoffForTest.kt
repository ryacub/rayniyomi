package eu.kanade.presentation.entries.components

import androidx.compose.ui.geometry.Rect
import eu.kanade.tachiyomi.ui.reader.CoverHandoff
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoverHandoffForTest {

    private val onScreen = Rect(left = 40f, top = 120f, right = 240f, bottom = 420f)

    @Test
    fun `unknown cover gives no handoff`() {
        assertNull(coverHandoffFor(null, 1080, 2400, "https://example.test/cover.jpg"))
    }

    @Test
    fun `cover without a url gives no handoff`() {
        assertNull(coverHandoffFor(onScreen, 1080, 2400, null))
    }

    @Test
    fun `cover scrolled off screen gives no handoff`() {
        val offScreen = Rect(left = 40f, top = 3000f, right = 240f, bottom = 3300f)

        assertNull(coverHandoffFor(offScreen, 1080, 2400, "https://example.test/cover.jpg"))
    }

    @Test
    fun `visible cover gives its rectangle and url`() {
        assertEquals(
            CoverHandoff(40f, 120f, 200f, 300f, "https://example.test/cover.jpg"),
            coverHandoffFor(onScreen, 1080, 2400, "https://example.test/cover.jpg"),
        )
    }

    @Test
    fun `taking a handoff hides the detail cover`() {
        val state = CoverHandoffState().apply { bounds = onScreen }

        val handoff = state.take(1080, 2400, "https://example.test/cover.jpg")

        assertEquals(CoverHandoff(40f, 120f, 200f, 300f, "https://example.test/cover.jpg"), handoff)
        assertTrue(state.hidden)
    }

    @Test
    fun `no handoff keeps the detail cover visible`() {
        val state = CoverHandoffState()

        assertNull(state.take(1080, 2400, "https://example.test/cover.jpg"))
        assertFalse(state.hidden)
    }

    @Test
    fun `revealing shows the detail cover again`() {
        val state = CoverHandoffState().apply { bounds = onScreen }
        state.take(1080, 2400, "https://example.test/cover.jpg")

        state.reveal()

        assertFalse(state.hidden)
    }
}
