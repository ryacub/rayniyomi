package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PagerConfigEffectiveTransitionStyleTest {

    @Test
    fun `CURL with scale 0 downgrades to SLIDE`() {
        effectiveTransitionStyle(PageTransitionStyle.CURL, 0f) shouldBe PageTransitionStyle.SLIDE
    }

    @Test
    fun `CURL with scale 1 stays CURL`() {
        effectiveTransitionStyle(PageTransitionStyle.CURL, 1f) shouldBe PageTransitionStyle.CURL
    }

    @Test
    fun `CURL with scale 05 stays CURL`() {
        effectiveTransitionStyle(PageTransitionStyle.CURL, 0.5f) shouldBe PageTransitionStyle.CURL
    }

    @Test
    fun `SLIDE with scale 0 stays SLIDE`() {
        effectiveTransitionStyle(PageTransitionStyle.SLIDE, 0f) shouldBe PageTransitionStyle.SLIDE
    }

    @Test
    fun `NONE with scale 0 stays NONE`() {
        effectiveTransitionStyle(PageTransitionStyle.NONE, 0f) shouldBe PageTransitionStyle.NONE
    }
}
