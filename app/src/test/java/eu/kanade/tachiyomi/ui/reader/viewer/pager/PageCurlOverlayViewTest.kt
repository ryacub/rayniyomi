package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class PageCurlOverlayViewTest {

    private val overlay = PageCurlOverlayView(mockk<Context>(relaxed = true))

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `captureBitmap returns a bitmap with the source dimensions`() {
        val source = mockk<View>(relaxed = true)
        every { source.width } returns 320
        every { source.height } returns 480
        val captured = mockk<Bitmap>(relaxed = true)
        every { captured.width } returns 320
        every { captured.height } returns 480

        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888) } returns captured
        mockkConstructor(Canvas::class)

        val result = overlay.captureBitmap(source)

        result shouldBe captured
        result?.width shouldBe 320
        result?.height shouldBe 480
    }

    @Test
    fun `captureBitmap returns null for a zero size source`() {
        val source = mockk<View>(relaxed = true)
        // A relaxed mock returns 0 for width and height.

        val result = overlay.captureBitmap(source)

        result shouldBe null
    }

    @Test
    fun `captureBitmap returns null when the bitmap allocation runs out of memory`() {
        val source = mockk<View>(relaxed = true)
        every { source.width } returns 320
        every { source.height } returns 480

        mockkStatic(Bitmap::class)
        every {
            Bitmap.createBitmap(any<Int>(), any<Int>(), Bitmap.Config.ARGB_8888)
        } throws OutOfMemoryError("no memory")

        val result = overlay.captureBitmap(source)

        result shouldBe null
    }

    @Test
    fun `captureBitmap returns null when the source draw fails`() {
        val source = mockk<View>(relaxed = true)
        every { source.width } returns 320
        every { source.height } returns 480
        val captured = mockk<Bitmap>(relaxed = true)

        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888) } returns captured
        mockkConstructor(Canvas::class)
        every { source.draw(any<Canvas>()) } throws IllegalStateException("bitmap is recycled")

        val result = overlay.captureBitmap(source)

        result shouldBe null
    }
}
