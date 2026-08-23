package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
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
        val fixture = Fixture()
        every { fixture.overlay.captureBitmap(fixture.sourceHolder) } returns null
        val fallbacks = mutableListOf<Boolean>()

        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            curlFromRight = true,
            advance = { fallbacks += it },
        )

        fallbacks.shouldContainExactly(true)
        verify(exactly = 0) { fixture.pager.setGestureDetectorEnabled(false) }
    }

    @Test
    fun `target capture failure recycles the source and restores input`() {
        val fixture = Fixture()
        val fromBitmap = fixture.bitmap()
        every { fixture.overlay.captureBitmap(fixture.sourceHolder) } returns fromBitmap
        every { fixture.overlay.captureBitmap(fixture.targetHolder) } returns null

        fixture.startCurl()

        verify(exactly = 1) { fromBitmap.recycle() }
        verify(exactly = 1) { fixture.pager.setGestureDetectorEnabled(true) }
    }

    @Test
    fun `normal completion hides the overlay and recycles both bitmaps`() {
        val fixture = Fixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.overlay.captureBitmap(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.endCallbacks.single().invoke()
        fixture.runNextPostedCallback()

        verify(exactly = 1) { fixture.overlay.isVisible = false }
        verify(exactly = 1) { fixture.overlay.cancelCurl() }
        verify(exactly = 1) { fromBitmap.recycle() }
        verify(exactly = 1) { toBitmap.recycle() }
        fixture.delayedCallbacks.size shouldBe 1

        fixture.delayedCallbacks.single().run()
        verify(exactly = 1) { fixture.pager.setGestureDetectorEnabled(true) }
    }

    @Test
    fun `superseded callback recycles only its bitmap pair`() {
        val fixture = Fixture()
        val firstFrom = fixture.bitmap()
        val firstTo = fixture.bitmap()
        val secondFrom = fixture.bitmap()
        val secondTo = fixture.bitmap()
        every { fixture.overlay.captureBitmap(any()) } returnsMany
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
        val fixture = Fixture()
        val firstFrom = fixture.bitmap()
        val firstTo = fixture.bitmap()
        val secondFrom = fixture.bitmap()
        val secondTo = fixture.bitmap()
        every { fixture.overlay.captureBitmap(any()) } returnsMany
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
        verify(exactly = 0) { fixture.pager.setGestureDetectorEnabled(true) }
    }

    @Test
    fun `fallback cancellation restores input`() {
        val fixture = Fixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.overlay.captureBitmap(any()) } returnsMany listOf(fromBitmap, toBitmap)
        val fallbacks = mutableListOf<Boolean>()

        fixture.startCurl()
        fixture.coordinator.runOrFallback(
            targetPosition = TARGET_POSITION,
            curlFromRight = true,
            advance = { fallbacks += it },
        )

        fallbacks.shouldContainExactly(true)
        verify(exactly = 1) { fixture.pager.setGestureDetectorEnabled(true) }
        verify(exactly = 1) { fromBitmap.recycle() }
        verify(exactly = 1) { toBitmap.recycle() }
    }

    @Test
    fun `release during cancellation does not post a callback after teardown`() {
        val fixture = Fixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.overlay.captureBitmap(any()) } returnsMany listOf(fromBitmap, toBitmap)

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
        val fixture = Fixture()
        val fromBitmap = fixture.bitmap()
        every { fixture.overlay.captureBitmap(fixture.sourceHolder) } returns fromBitmap

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
        val fixture = Fixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.overlay.captureBitmap(any()) } returnsMany listOf(fromBitmap, toBitmap)

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
        val fixture = Fixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.overlay.captureBitmap(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.endCallbacks.single().invoke()
        fixture.runNextPostedCallback()
        val gestureCallback = fixture.delayedCallbacks.single()

        fixture.coordinator.release()

        verify(exactly = 1) { fixture.pager.removeCallbacks(gestureCallback) }
        verify(exactly = 2) { fixture.overlay.cancelCurl() }
    }

    private class Fixture {
        val pager = mockk<Pager>(relaxed = true)
        val overlay = mockk<PageCurlOverlayView>(relaxed = true)
        val sourceHolder = mockk<PagerPageHolder>(relaxed = true)
        val targetHolder = mockk<PagerPageHolder>(relaxed = true)
        private val targetPage = ReaderPage(TARGET_POSITION)
        val endCallbacks = mutableListOf<() -> Unit>()
        val delayedCallbacks = mutableListOf<Runnable>()
        private val postedCallbacks = ArrayDeque<Runnable>()
        var nowMs = 1_000L

        val coordinator = PageCurlCoordinator(
            overlay = overlay,
            pager = pager,
            storedTransitionStyle = { PageTransitionStyle.CURL },
            effectiveTransitionStyle = { PageTransitionStyle.CURL },
            sourceHolder = { sourceHolder },
            itemAt = { targetPage },
            holderFor = { targetHolder },
            nowMs = { nowMs },
        )

        init {
            every { sourceHolder.isAtMinimumZoom() } returns true
            every { targetHolder.isAtMinimumZoom() } returns true
            every { targetHolder.isLaidOut } returns true
            every { pager.currentItem } returns TARGET_POSITION
            every { pager.post(any()) } answers {
                postedCallbacks.addLast(firstArg<Runnable>())
                true
            }
            every { pager.postDelayed(any(), any()) } answers {
                delayedCallbacks += firstArg<Runnable>()
                true
            }
            every { pager.removeCallbacks(any()) } answers {
                val runnable = firstArg<Runnable>()
                postedCallbacks.remove(runnable) || delayedCallbacks.remove(runnable)
            }
            every {
                overlay.playCurl(
                    from = any(),
                    to = any(),
                    curlFromRight = any(),
                    durationMs = any(),
                    onEnd = any(),
                )
            } answers {
                endCallbacks += arg<() -> Unit>(4)
            }
        }

        fun startCurl() {
            coordinator.runOrFallback(
                targetPosition = TARGET_POSITION,
                curlFromRight = true,
                advance = {},
            )
            runNextPostedCallback()
        }

        fun runNextPostedCallback() {
            postedCallbacks.removeFirst().run()
        }

        fun nextPostedCallback(): Runnable {
            return postedCallbacks.first()
        }

        fun bitmap(): Bitmap {
            var recycled = false
            return mockk {
                every { isRecycled } answers { recycled }
                every { recycle() } answers { recycled = true }
            }
        }
    }

    private companion object {
        const val TARGET_POSITION = 1
    }
}
