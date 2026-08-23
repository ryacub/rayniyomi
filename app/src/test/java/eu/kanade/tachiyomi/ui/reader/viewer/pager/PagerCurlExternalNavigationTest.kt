package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class PagerCurlExternalNavigationTest {

    @Test
    fun `external page change during a curl cancels it and restores input`() {
        val fixture = Fixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.overlay.captureBitmap(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.coordinator.onPageChangedExternally(TARGET_POSITION + 5)

        verify(exactly = 1) { fixture.overlay.cancelCurl() }
        verify(exactly = 1) { fixture.overlay.isVisible = false }
        verify(exactly = 1) { fromBitmap.recycle() }
        verify(exactly = 1) { toBitmap.recycle() }
        verify(exactly = 1) { fixture.pager.setGestureDetectorEnabled(true) }
    }

    @Test
    fun `own advance does not cancel the curl`() {
        val fixture = Fixture()
        val fromBitmap = fixture.bitmap()
        val toBitmap = fixture.bitmap()
        every { fixture.overlay.captureBitmap(any()) } returnsMany listOf(fromBitmap, toBitmap)

        fixture.startCurl()
        fixture.coordinator.onPageChangedExternally(TARGET_POSITION)

        verify(exactly = 0) { fixture.overlay.cancelCurl() }
        verify(exactly = 0) { fixture.pager.setGestureDetectorEnabled(true) }
    }

    @Test
    fun `external change after release is ignored`() {
        val fixture = Fixture()
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

    private class Fixture {
        val pager = mockk<Pager>(relaxed = true)
        val overlay = mockk<PageCurlOverlayView>(relaxed = true)
        val sourceHolder = mockk<PagerPageHolder>(relaxed = true)
        val targetHolder = mockk<PagerPageHolder>(relaxed = true)
        private val pages = List(TARGET_POSITION + 4) { ReaderPage(it) }
        val endCallbacks = mutableListOf<() -> Unit>()
        private val postedCallbacks = ArrayDeque<Runnable>()

        val coordinator = PageCurlCoordinator(
            overlay = overlay,
            pager = pager,
            storedTransitionStyle = { PageTransitionStyle.CURL },
            effectiveTransitionStyle = { PageTransitionStyle.CURL },
            sourceHolder = { sourceHolder },
            itemAt = { pages.getOrNull(it) },
            holderFor = { targetHolder },
            nowMs = { 1_000L },
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
            every { pager.removeCallbacks(any()) } answers {
                postedCallbacks.remove(firstArg<Runnable>())
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
            postedCallbacks.removeFirst().run()
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
