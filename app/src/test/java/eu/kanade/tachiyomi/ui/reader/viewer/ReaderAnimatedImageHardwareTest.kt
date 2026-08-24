package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderAnimatedImageHardwareTest {

    @Test
    fun `a pager animated page uses software bitmaps`() {
        assertFalse(animatedImageAllowHardware(isWebtoon = false))
    }

    @Test
    fun `a webtoon animated page keeps hardware bitmaps`() {
        assertTrue(animatedImageAllowHardware(isWebtoon = true))
    }
}
