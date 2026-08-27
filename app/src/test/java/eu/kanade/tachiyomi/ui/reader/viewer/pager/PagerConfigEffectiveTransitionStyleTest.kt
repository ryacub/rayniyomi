package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.animation.ValueAnimator
import android.os.Build
import android.provider.Settings
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class PagerConfigEffectiveTransitionStyleTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `CURL follows live animator scale when Settings Global reports animations enabled`() {
        mockkStatic(Settings.Global::class)
        mockkStatic(ValueAnimator::class)
        every { Settings.Global.getFloat(any(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) } returns 1f
        every { ValueAnimator.getDurationScale() } returns 0f

        val settingsScale = Settings.Global.getFloat(
            mockk(relaxed = true),
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        )

        effectiveTransitionStyle(
            PageTransitionStyle.CURL,
            liveAnimatorDurationScale(Build.VERSION_CODES.TIRAMISU),
        ) shouldBe
            PageTransitionStyle.SLIDE
        settingsScale shouldBe 1f
    }

    @Test
    fun `CURL stays enabled when live animator scale is 1`() {
        mockkStatic(ValueAnimator::class)
        every { ValueAnimator.getDurationScale() } returns 1f

        effectiveTransitionStyle(
            PageTransitionStyle.CURL,
            liveAnimatorDurationScale(Build.VERSION_CODES.TIRAMISU),
        ) shouldBe PageTransitionStyle.CURL
    }

    @Test
    fun `live animator scale is zero when animators are disabled below API 33`() {
        mockkStatic(ValueAnimator::class)
        every { ValueAnimator.areAnimatorsEnabled() } returns false

        liveAnimatorDurationScale(Build.VERSION_CODES.O) shouldBe 0f
    }

    @Test
    fun `live animator scale is enabled when animators are enabled below API 33`() {
        mockkStatic(ValueAnimator::class)
        every { ValueAnimator.areAnimatorsEnabled() } returns true

        liveAnimatorDurationScale(Build.VERSION_CODES.O) shouldBe 1f
    }

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
