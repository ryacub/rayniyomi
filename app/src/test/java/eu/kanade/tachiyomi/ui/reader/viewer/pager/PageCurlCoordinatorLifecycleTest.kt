package eu.kanade.tachiyomi.ui.reader.viewer.pager

import androidx.core.view.isVisible
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class PageCurlCoordinatorLifecycleTest {

    @Test
    fun `source capture failure uses the current animated fallback`() {
        val fixture = PageCurlCoordinatorFixture()
        every { fixture.capture.capture(fixture.sourceHolder) } returns null
        val fallbacks = mutableListOf<Boolean>()

        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            curlFromRight = true,
            advance = { fallbacks += it },
        )

        fallbacks.shouldContainExactly(true)
        fixture.inputEnabled shouldBe true
        // No curl state existed before this run, so the fallback must not
        // touch the overlay.
        verify(exactly = 0) { fixture.overlay.cancelCurl() }
        verify(exactly = 0) { fixture.overlay.isVisible = false }
    }

    @Test
    fun `target capture failure routes through the shared teardown`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        every { fixture.capture.capture(fixture.sourceHolder) } returns fromBitmap
        every { fixture.capture.capture(fixture.targetHolder) } returns null

        fixture.startCurl()

        verify(exactly = 1) { fromBitmap.recycle() }
        fixture.inputEnabled shouldBe true
        // GREEN only after R918 hoists activeFromBitmap before the target
        // capture; without that hoist the state is empty and the shared
        // teardown early-outs without touching the overlay.
        verify(exactly = 1) { fixture.overlay.cancelCurl() }
        verify(exactly = 1) { fixture.overlay.isVisible = false }
    }

    @Test
    fun `target capture failure recycles the source and restores input`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        every { fixture.capture.capture(fixture.sourceHolder) } returns fromBitmap
        every { fixture.capture.capture(fixture.targetHolder) } returns null

        fixture.startCurl()

        verify(exactly = 1) { fromBitmap.recycle() }
        fixture.inputEnabled shouldBe true
    }

    @Test
    fun `normal completion hides the overlay and recycles both bitmaps`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.endCallbacks.single().invoke()
        fixture.runNextPostedCallback()

        verify(exactly = 1) { fixture.overlay.isVisible = false }
        verify(exactly = 1) { fixture.overlay.cancelCurl() }
        verify(exactly = 1) { fromBitmap.recycle() }
        verify(exactly = 1) { toBitmap.recycle() }
        fixture.delayedCallbacks.size shouldBe 1

        // The curl claim stays active until the delayed callback releases it.
        fixture.inputEnabled shouldBe false
        fixture.delayedCallbacks.single().run()
        fixture.inputEnabled shouldBe true
    }

    @Test
    fun `superseded callback recycles only its bitmap pair`() {
        val fixture = PageCurlCoordinatorFixture()
        val firstFrom = fixture.bitmap()
        val firstTo = fixture.bitmap()
        val secondFrom = fixture.bitmap()
        val secondTo = fixture.bitmap()
        every { fixture.capture.capture(any()) } returnsMany
            listOf(firstFrom, firstTo, secondFrom, secondTo)

        fixture.startCurl()
        fixture.nowMs += 1_000L
        fixture.startCurl()
        fixture.endCallbacks.first().invoke()

        verify(exactly = 1) { firstFrom.recycle() }
        verify(exactly = 1) { firstTo.recycle() }
        verify(exactly = 0) { secondFrom.recycle() }
        verify(exactly = 0) { secondTo.recycle() }
        verify(exactly = 2) { fixture.pager.post(any()) }
    }

    @Test
    fun `stale layout callback cannot cancel a newer curl`() {
        val fixture = PageCurlCoordinatorFixture()
        val firstFrom = fixture.bitmap()
        val firstTo = fixture.bitmap()
        val secondFrom = fixture.bitmap()
        val secondTo = fixture.bitmap()
        every { fixture.capture.capture(any()) } returnsMany
            listOf(firstFrom, firstTo, secondFrom, secondTo)

        fixture.startCurl()
        fixture.endCallbacks.first().invoke()
        val staleLayoutCallback = fixture.nextPostedCallback()

        fixture.nowMs += 1_000L
        fixture.startCurl()
        staleLayoutCallback.run()

        verify(exactly = 1) { fixture.overlay.cancelCurl() }
        verify(exactly = 0) { secondFrom.recycle() }
        verify(exactly = 0) { secondTo.recycle() }
        fixture.inputEnabled shouldBe false
    }

    @Test
    fun `fallback cancellation restores input`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)
        val fallbacks = mutableListOf<Boolean>()

        fixture.startCurl()
        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            curlFromRight = true,
            advance = { fallbacks += it },
        )

        fallbacks.shouldContainExactly(true)
        fixture.inputEnabled shouldBe true
        verify(exactly = 1) { fromBitmap.recycle() }
        verify(exactly = 1) { toBitmap.recycle() }
    }

    @Test
    fun `release during cancellation does not post a callback after teardown`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        every { fixture.overlay.cancelCurl() } answers {
            fixture.endCallbacks.single().invoke()
        }

        fixture.coordinator.release()

        verify(exactly = 1) { fixture.overlay.cancelCurl() }
        verify(exactly = 1) { fixture.pager.post(any()) }
        verify(exactly = 1) { fromBitmap.recycle() }
        verify(exactly = 1) { toBitmap.recycle() }
    }

    @Test
    fun `release removes a target poll and recycles its pending bitmap`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        every { fixture.capture.capture(fixture.sourceHolder) } returns fromBitmap

        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            curlFromRight = true,
            advance = {},
        )
        val targetPoll = fixture.nextPostedCallback()
        fixture.coordinator.release()

        verify(exactly = 1) { fixture.pager.removeCallbacks(targetPoll) }
        verify(exactly = 1) { fromBitmap.recycle() }
    }

    @Test
    fun `release removes the pending layout callback`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.endCallbacks.single().invoke()
        val layoutCallback = fixture.nextPostedCallback()

        fixture.coordinator.release()

        verify(exactly = 1) { fixture.pager.removeCallbacks(layoutCallback) }
        verify(exactly = 1) { fixture.overlay.cancelCurl() }
        verify(exactly = 1) { fromBitmap.recycle() }
        verify(exactly = 1) { toBitmap.recycle() }
    }

    @Test
    fun `release removes the pending gesture callback`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.endCallbacks.single().invoke()
        fixture.runNextPostedCallback()
        val gestureCallback = fixture.delayedCallbacks.single()

        fixture.coordinator.release()

        verify(exactly = 1) { fixture.pager.removeCallbacks(gestureCallback) }
        verify(exactly = 2) { fixture.overlay.cancelCurl() }
    }

    @Test
    fun `four rapid taps inside one second advance four pages`() {
        val fixture = PageCurlCoordinatorFixture()

        fixture.simulateTap(TARGET_POSITION)
        repeat(3) { index ->
            fixture.nowMs += 100L
            fixture.simulateTap(TARGET_POSITION + index + 1)
        }

        fixture.currentItemIndex shouldBe TARGET_POSITION + 3
        verify(exactly = 1) {
            fixture.overlay.playCurl(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `snap taps keep input enabled between turns`() {
        val fixture = PageCurlCoordinatorFixture()

        fixture.simulateTap(TARGET_POSITION)
        repeat(3) { index ->
            fixture.nowMs += 100L
            fixture.simulateTap(TARGET_POSITION + index + 1)
            fixture.inputEnabled shouldBe true
        }
    }

    @Test
    fun `normal completion restores input once through the delayed callback`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.endCallbacks.single().invoke()
        fixture.runNextPostedCallback()
        fixture.inputEnabled shouldBe false
        fixture.delayedCallbacks.single().run()
        fixture.inputEnabled shouldBe true
    }

    @Test
    fun `stale callbacks from a superseded curl cannot affect a newer curl`() {
        val fixture = PageCurlCoordinatorFixture()
        val firstFrom = fixture.bitmap()
        val firstTo = fixture.bitmap()
        val secondFrom = fixture.bitmap()
        val secondTo = fixture.bitmap()
        every { fixture.capture.capture(any()) } returnsMany
            listOf(firstFrom, firstTo, secondFrom, secondTo)

        fixture.startCurl()
        fixture.endCallbacks.first().invoke()
        val staleLayoutCallback = fixture.nextPostedCallback()

        fixture.nowMs += 1_000L
        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION + 1,
            curlFromRight = true,
            advance = {},
        )
        fixture.runNewestPostedCallback()

        staleLayoutCallback.run()
        verify(exactly = 1) { fixture.overlay.cancelCurl() }
        verify(exactly = 1) { fixture.overlay.isVisible = false }
        fixture.delayedCallbacks.size shouldBe 0
        verify(exactly = 0) { secondFrom.recycle() }
        verify(exactly = 0) { secondTo.recycle() }

        fixture.coordinator.onPageChangedExternally(TARGET_POSITION + 5)
        verify(exactly = 2) { fixture.overlay.cancelCurl() }
        verify(exactly = 2) { fixture.overlay.isVisible = false }
        verify(exactly = 1) { secondFrom.recycle() }
        verify(exactly = 1) { secondTo.recycle() }
        fixture.inputEnabled shouldBe true
    }

    private companion object {
        const val TARGET_POSITION = 1
    }
}
