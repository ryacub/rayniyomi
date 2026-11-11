package eu.kanade.tachiyomi.ui.reader.viewer

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ReaderPageImageViewScaledDurationTest {

    @Test
    fun `live scale 0 makes the animation one step even when Settings reports 1`() {
        scaledAnimationDuration(baseMs = 500, liveScale = 0f, settingsScale = 1f) shouldBe 1
    }

    @Test
    fun `live scale 1 keeps the base duration`() {
        scaledAnimationDuration(baseMs = 250, liveScale = 1f, settingsScale = 1f) shouldBe 250
    }

    @Test
    fun `a nonzero live scale keeps the Settings multiplier`() {
        scaledAnimationDuration(baseMs = 500, liveScale = 1f, settingsScale = 0.5f) shouldBe 250
    }
}
