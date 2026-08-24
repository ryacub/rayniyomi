package eu.kanade.tachiyomi.ui.reader.viewer.pager

import androidx.core.view.isVisible
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
        every { fixture.overlay.captureBitmap(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.coordinator.onPageChangedExternally(TARGET_POSITION + 5)

        verify(exactly = 1) { fixture.overlay.cancelCurl() }
        verify(exactly = 1) { fixture.overlay.isVisible = false }
        verify(exactly = 1) { fromBitmap.recycle() }
        verify(exactly = 1) { toBitmap.recycle() }
        fixture.inputEnabled shouldBe true
    }

    @Test
    fun `own advance does not cancel the curl`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.overlay.captureBitmap(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.coordinator.onPageChangedExternally(TARGET_POSITION)

        verify(exactly = 0) { fixture.overlay.cancelCurl() }
        fixture.inputEnabled shouldBe false
    }

    @Test
    fun `external change after release is ignored`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        every { fixture.overlay.captureBitmap(fixture.sourceHolder) } returns fromBitmap

        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            curlFromRight = true,
            advance = {},
        )
        fixture.coordinator.release()
        fixture.coordinator.onPageChangedExternally(TARGET_POSITION + 5)

        verify(exactly = 1) { fixture.overlay.cancelCurl() }
    }

    private companion object {
        const val TARGET_POSITION = 1
    }
}
