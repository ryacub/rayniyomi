package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class PagerCurlExternalNavigationTest {

    @Test
    fun `external page change during a curl cancels it and restores input`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.coordinator.onPageChangedExternally(TARGET_POSITION + 5)

        verify(exactly = 1) { fixture.overlay.abortAndHide() }
        verify(exactly = 1) { fromBitmap.recycle() }
        verify(exactly = 1) { toBitmap.recycle() }
        fixture.inputEnabled shouldBe true
    }

    @Test
    fun `own advance does not cancel the curl`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.coordinator.onPageChangedExternally(TARGET_POSITION)

        verify(exactly = 0) { fixture.overlay.abortAndHide() }
        fixture.inputEnabled shouldBe false
    }

    @Test
    fun `external change after release is ignored`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        every { fixture.capture.capture(fixture.sourceHolder) } returns fromBitmap

        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            direction = CurlDirection.FROM_RIGHT,
            advance = {},
        )
        fixture.coordinator.release()
        fixture.coordinator.onPageChangedExternally(TARGET_POSITION + 5)

        verify(exactly = 1) { fixture.overlay.abortAndHide() }
    }

    private companion object {
        const val TARGET_POSITION = 1
    }
}
