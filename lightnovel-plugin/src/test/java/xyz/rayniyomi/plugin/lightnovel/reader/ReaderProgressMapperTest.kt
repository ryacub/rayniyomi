package xyz.rayniyomi.plugin.lightnovel.reader

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ReaderProgressMapperTest {

    @Test
    fun `offsetToScrollY returns zero for empty chapter or no scroll range`() {
        assertEquals(0, ReaderProgressMapper.offsetToScrollY(charOffset = 5, chapterLength = 0, maxScrollY = 300))
        assertEquals(0, ReaderProgressMapper.offsetToScrollY(charOffset = 5, chapterLength = 100, maxScrollY = 0))
    }

    @Test
    fun `offsetToScrollY clamps to bounds`() {
        assertEquals(0, ReaderProgressMapper.offsetToScrollY(charOffset = -10, chapterLength = 100, maxScrollY = 200))
        assertEquals(200, ReaderProgressMapper.offsetToScrollY(charOffset = 999, chapterLength = 100, maxScrollY = 200))
    }

    @Test
    fun `scrollYToOffset returns zero for empty chapter or no scroll range`() {
        assertEquals(0, ReaderProgressMapper.scrollYToOffset(scrollY = 10, chapterLength = 0, maxScrollY = 100))
        assertEquals(0, ReaderProgressMapper.scrollYToOffset(scrollY = 10, chapterLength = 100, maxScrollY = 0))
    }

    @Test
    fun `scrollYToOffset clamps to bounds`() {
        assertEquals(0, ReaderProgressMapper.scrollYToOffset(scrollY = -1, chapterLength = 100, maxScrollY = 200))
        assertEquals(100, ReaderProgressMapper.scrollYToOffset(scrollY = 999, chapterLength = 100, maxScrollY = 200))
    }

    @Test
    fun `round-trip stays close to original offset`() {
        val chapterLength = 1000
        val maxScroll = 500
        val originalOffset = 437

        val scrollY = ReaderProgressMapper.offsetToScrollY(originalOffset, chapterLength, maxScroll)
        val recoveredOffset = ReaderProgressMapper.scrollYToOffset(scrollY, chapterLength, maxScroll)

        assertTrue(abs(recoveredOffset - originalOffset) <= 3)
    }
}
