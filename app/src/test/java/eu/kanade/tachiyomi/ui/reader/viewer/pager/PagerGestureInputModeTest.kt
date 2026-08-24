package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.viewer.pager.Pager.GestureInputMode.DISABLED
import eu.kanade.tachiyomi.ui.reader.viewer.pager.Pager.GestureInputMode.ENABLED
import eu.kanade.tachiyomi.ui.reader.viewer.pager.Pager.GestureInputMode.SUPPRESS_CHROME
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Pins the gesture input mode matrix. Actual touch delivery through [Pager.dispatchTouchEvent]
 * needs an Android ViewPager, so it stays covered by the device smoke checklist in the PR body.
 */
class PagerGestureInputModeTest {

    @Test
    fun `enabled mode feeds the tap detector and honors long taps`() {
        Pager.feedsTapDetector(ENABLED) shouldBe true
        Pager.honorsLongTap(ENABLED) shouldBe true
    }

    @Test
    fun `suppress chrome mode feeds the tap detector and blocks long taps`() {
        Pager.feedsTapDetector(SUPPRESS_CHROME) shouldBe true
        Pager.honorsLongTap(SUPPRESS_CHROME) shouldBe false
    }

    @Test
    fun `disabled mode blocks both tap detection and long taps`() {
        Pager.feedsTapDetector(DISABLED) shouldBe false
        Pager.honorsLongTap(DISABLED) shouldBe false
    }
}
