package eu.kanade.tachiyomi.ui.reader.viewer.pager

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
            direction = CurlDirection.FROM_RIGHT,
            advance = { fallbacks += it },
        )

        fallbacks.shouldContainExactly(true)
        fixture.inputEnabled shouldBe true
        // No curl state existed before this run, so the fallback must not
        // touch the overlay.
        verify(exactly = 0) { fixture.overlay.abortAndHide() }
    }

    @Test
    fun `target capture failure routes through the shared teardown`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.page()
        every { fixture.capture.capture(fixture.sourceHolder) } returns fromBitmap
        every { fixture.capture.capture(fixture.targetHolder) } returns null

        fixture.startCurl()

        verify(exactly = 1) { fromBitmap.bitmap.recycle() }
        fixture.inputEnabled shouldBe true
        // GREEN only after R918 hoists activeFromBitmap before the target
        // capture; without that hoist the state is empty and the shared
        // teardown early-outs without touching the overlay.
        verify(exactly = 1) { fixture.overlay.abortAndHide() }
    }

    @Test
    fun `target capture failure recycles the source and restores input`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.page()
        every { fixture.capture.capture(fixture.sourceHolder) } returns fromBitmap
        every { fixture.capture.capture(fixture.targetHolder) } returns null

        fixture.startCurl()

        verify(exactly = 1) { fromBitmap.bitmap.recycle() }
        fixture.inputEnabled shouldBe true
    }

    @Test
    fun `normal completion hides the overlay and recycles both bitmaps`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.page()
        val toBitmap = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.endCallbacks.single().invoke()
        fixture.runNextPostedCallback()

        verify(exactly = 1) { fixture.overlay.abortAndHide() }
        verify(exactly = 1) { fromBitmap.bitmap.recycle() }
        verify(exactly = 1) { toBitmap.bitmap.recycle() }
        fixture.delayedCallbacks.size shouldBe 1

        // The curl claim stays active until the delayed callback releases it.
        fixture.inputEnabled shouldBe false
        fixture.delayedCallbacks.single().run()
        fixture.inputEnabled shouldBe true
    }

    @Test
    fun `a curl captures exactly the outgoing and incoming bitmaps`() {
        val fixture = PageCurlCoordinatorFixture()
        val onlyBitmap = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany listOf(onlyBitmap)

        fixture.startCurl()

        // One capture for the outgoing page and one for the incoming page.
        // A third capture would introduce a third tracked bitmap slot.
        verify(exactly = 2) { fixture.capture.capture(any()) }
    }

    @Test
    fun `superseded callback recycles only its bitmap pair`() {
        val fixture = PageCurlCoordinatorFixture()
        val firstFrom = fixture.page()
        val firstTo = fixture.page()
        val secondFrom = fixture.page()
        val secondTo = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany
            listOf(firstFrom, firstTo, secondFrom, secondTo)

        fixture.startCurl()
        fixture.nowMs += 1_000L
        fixture.startCurl()
        fixture.endCallbacks.first().invoke()

        verify(exactly = 1) { firstFrom.bitmap.recycle() }
        verify(exactly = 1) { firstTo.bitmap.recycle() }
        verify(exactly = 0) { secondFrom.bitmap.recycle() }
        verify(exactly = 0) { secondTo.bitmap.recycle() }
        verify(exactly = 2) { fixture.pager.post(any()) }
    }

    @Test
    fun `stale layout callback cannot cancel a newer curl`() {
        val fixture = PageCurlCoordinatorFixture()
        val firstFrom = fixture.page()
        val firstTo = fixture.page()
        val secondFrom = fixture.page()
        val secondTo = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany
            listOf(firstFrom, firstTo, secondFrom, secondTo)

        fixture.startCurl()
        fixture.endCallbacks.first().invoke()
        val staleLayoutCallback = fixture.nextPostedCallback()

        fixture.nowMs += 1_000L
        fixture.startCurl()
        staleLayoutCallback.run()

        verify(exactly = 1) { fixture.overlay.abortAndHide() }
        verify(exactly = 0) { secondFrom.bitmap.recycle() }
        verify(exactly = 0) { secondTo.bitmap.recycle() }
        fixture.inputEnabled shouldBe false
    }

    @Test
    fun `fallback cancellation restores input`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.page()
        val toBitmap = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)
        val fallbacks = mutableListOf<Boolean>()

        fixture.startCurl()
        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            direction = CurlDirection.FROM_RIGHT,
            advance = { fallbacks += it },
        )

        fallbacks.shouldContainExactly(true)
        fixture.inputEnabled shouldBe true
        verify(exactly = 1) { fromBitmap.bitmap.recycle() }
        verify(exactly = 1) { toBitmap.bitmap.recycle() }
    }

    @Test
    fun `release during cancellation does not post a callback after teardown`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.page()
        val toBitmap = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        every { fixture.overlay.abortAndHide() } answers {
            fixture.endCallbacks.single().invoke()
        }

        fixture.coordinator.release()

        verify(exactly = 1) { fixture.overlay.abortAndHide() }
        verify(exactly = 1) { fixture.pager.post(any()) }
        verify(exactly = 1) { fromBitmap.bitmap.recycle() }
        verify(exactly = 1) { toBitmap.bitmap.recycle() }
    }

    @Test
    fun `release removes a target poll and recycles its pending bitmap`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.page()
        every { fixture.capture.capture(fixture.sourceHolder) } returns fromBitmap

        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            direction = CurlDirection.FROM_RIGHT,
            advance = {},
        )
        val targetPoll = fixture.nextPostedCallback()
        fixture.coordinator.release()

        verify(exactly = 1) { fixture.pager.removeCallbacks(targetPoll) }
        verify(exactly = 1) { fromBitmap.bitmap.recycle() }
    }

    @Test
    fun `release removes the pending layout callback`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.page()
        val toBitmap = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.endCallbacks.single().invoke()
        val layoutCallback = fixture.nextPostedCallback()

        fixture.coordinator.release()

        verify(exactly = 1) { fixture.pager.removeCallbacks(layoutCallback) }
        verify(exactly = 1) { fixture.overlay.abortAndHide() }
        verify(exactly = 1) { fromBitmap.bitmap.recycle() }
        verify(exactly = 1) { toBitmap.bitmap.recycle() }
    }

    @Test
    fun `release removes the pending gesture callback`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.page()
        val toBitmap = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.endCallbacks.single().invoke()
        fixture.runNextPostedCallback()
        val gestureCallback = fixture.delayedCallbacks.single()

        fixture.coordinator.release()

        verify(exactly = 1) { fixture.pager.removeCallbacks(gestureCallback) }
        verify(exactly = 2) { fixture.overlay.abortAndHide() }
    }

    @Test
    fun `a fired gesture callback releases its slot`() {
        val fixture = PageCurlCoordinatorFixture()
        every { fixture.capture.capture(any()) } returnsMany
            listOf(fixture.page(), fixture.page(), fixture.page(), fixture.page())

        fixture.startCurl()
        fixture.endCallbacks.first().invoke()
        fixture.runNextPostedCallback()
        val gestureCallback = fixture.delayedCallbacks.single()
        gestureCallback.run()

        // The fired callback released its slot: the next teardown must not
        // remove it from the pager again.
        fixture.nowMs += 1_000L
        fixture.startCurl()

        verify(exactly = 0) { fixture.pager.removeCallbacks(gestureCallback) }
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
        val fromBitmap = fixture.page()
        val toBitmap = fixture.page()
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
        val firstFrom = fixture.page()
        val firstTo = fixture.page()
        val secondFrom = fixture.page()
        val secondTo = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany
            listOf(firstFrom, firstTo, secondFrom, secondTo)

        fixture.startCurl()
        fixture.endCallbacks.first().invoke()
        val staleLayoutCallback = fixture.nextPostedCallback()

        fixture.nowMs += 1_000L
        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION + 1,
            direction = CurlDirection.FROM_RIGHT,
            advance = {},
        )
        fixture.runNewestPostedCallback()

        staleLayoutCallback.run()
        verify(exactly = 1) { fixture.overlay.abortAndHide() }
        fixture.delayedCallbacks.size shouldBe 0
        verify(exactly = 0) { secondFrom.bitmap.recycle() }
        verify(exactly = 0) { secondTo.bitmap.recycle() }

        fixture.coordinator.onPageChangedExternally(TARGET_POSITION + 5)
        verify(exactly = 2) { fixture.overlay.abortAndHide() }
        verify(exactly = 1) { secondFrom.bitmap.recycle() }
        verify(exactly = 1) { secondTo.bitmap.recycle() }
        fixture.inputEnabled shouldBe true
    }

    @Test
    fun `teardown bumps the generation before the overlay aborts`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.page()
        val toBitmap = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        // Reenter through the animation-end callback while the abort runs.
        // The bump must land first so this callback takes the stale branch.
        every { fixture.overlay.abortAndHide() } answers {
            fixture.endCallbacks.single().invoke()
        }

        fixture.coordinator.onPageChangedExternally(TARGET_POSITION + 5)

        verify(exactly = 1) { fixture.overlay.abortAndHide() }
        verify(exactly = 1) { fromBitmap.bitmap.recycle() }
        verify(exactly = 1) { toBitmap.bitmap.recycle() }
        verify(exactly = 1) { fixture.pager.post(any()) } // target poll only
        fixture.delayedCallbacks.size shouldBe 0 // stays 0 under both orders
        fixture.inputEnabled shouldBe true // finish completed
    }

    @Test
    fun `teardown recycles the bitmaps before it aborts the overlay`() {
        val fixture = PageCurlCoordinatorFixture()
        val fromBitmap = fixture.page()
        val toBitmap = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        var recycledDuringAbort: Boolean? = null
        every { fixture.overlay.abortAndHide() } answers {
            recycledDuringAbort = fromBitmap.bitmap.isRecycled
        }

        fixture.coordinator.onPageChangedExternally(TARGET_POSITION + 5)

        recycledDuringAbort shouldBe true
        verify(exactly = 1) { fromBitmap.bitmap.recycle() }
        verify(exactly = 1) { toBitmap.bitmap.recycle() }
    }

    @Test
    fun `normal run pins every transition`() {
        val fixture = PageCurlCoordinatorFixture()
        every { fixture.capture.capture(any()) } returnsMany
            listOf(fixture.page(), fixture.page())

        fixture.startCurl()

        // Animating: the overlay plays exactly one curl and no poll runs.
        fixture.endCallbacks.size shouldBe 1
        fixture.pendingPostedCount shouldBe 0

        fixture.endCallbacks.first().invoke()

        // Waiting for layout: exactly one layout poll is posted.
        fixture.pendingPostedCount shouldBe 1

        fixture.runNextPostedCallback()

        // Terminal: teardown leaves only the delayed gesture reenable.
        fixture.pendingPostedCount shouldBe 0
        fixture.delayedCallbacks.size shouldBe 1
    }

    @Test
    fun `intermediate waiting state is observable`() {
        val fixture = PageCurlCoordinatorFixture()

        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            direction = CurlDirection.FROM_RIGHT,
            advance = {},
        )

        // Waiting for the target holder: exactly one poll is posted.
        fixture.pendingPostedCount shouldBe 1

        fixture.runNextPostedCallback()
        fixture.endCallbacks.single().invoke()
        fixture.runNextPostedCallback()

        fixture.pendingPostedCount shouldBe 0
        fixture.delayedCallbacks.size shouldBe 1
    }

    @Test
    fun `source capture failure leaves no tracked curl`() {
        val fixture = PageCurlCoordinatorFixture()
        every { fixture.capture.capture(fixture.sourceHolder) } returns null

        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            direction = CurlDirection.FROM_RIGHT,
            advance = {},
        )

        fixture.pendingPostedCount shouldBe 0
        fixture.inputEnabled shouldBe true
    }

    @Test
    fun `release during an active curl clears tracked state`() {
        val fixture = PageCurlCoordinatorFixture()

        val fromBitmap = fixture.page()
        val toBitmap = fixture.page()
        every { fixture.capture.capture(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()

        fixture.endCallbacks.size shouldBe 1

        fixture.coordinator.release()

        verify(exactly = 1) { fromBitmap.bitmap.recycle() }
        verify(exactly = 1) { toBitmap.bitmap.recycle() }
        fixture.pendingPostedCount shouldBe 0
        fixture.delayedCallbacks.size shouldBe 0
    }

    private companion object {
        const val TARGET_POSITION = 1
    }
}
