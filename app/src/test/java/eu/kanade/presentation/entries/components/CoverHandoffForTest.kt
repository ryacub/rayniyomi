package eu.kanade.presentation.entries.components

import androidx.compose.ui.geometry.Rect
import eu.kanade.tachiyomi.ui.reader.CoverHandoff
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
}
