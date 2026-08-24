package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class PageCurlCaptureTest {

    private val capture = PageCurlCapture()

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `capture returns a bitmap with the source dimensions`() {
        val source = mockk<View>(relaxed = true)
        every { source.width } returns 320
        every { source.height } returns 480
        val captured = mockk<Bitmap>(relaxed = true)
        every { captured.width } returns 320
        every { captured.height } returns 480

        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888) } returns captured
        mockkConstructor(Canvas::class)

        // A trivial sampler reports a visible pixel everywhere.
        val capture = PageCurlCapture { _, _, _ -> Color.WHITE }
        val result = capture.capture(source)

        result shouldBe captured
        result?.width shouldBe 320
        result?.height shouldBe 480
    }

    @Test
    fun `capture returns null when the draw leaves every pixel transparent`() {
        val source = mockk<View>(relaxed = true)
        every { source.width } returns 320
        every { source.height } returns 480
        val captured = mockk<Bitmap>(relaxed = true)

        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888) } returns captured
        mockkConstructor(Canvas::class)

        // A trivial sampler reports every pixel as transparent.
        val capture = PageCurlCapture { _, _, _ -> Color.TRANSPARENT }
        val result = capture.capture(source)

        result shouldBe null
    }

    @Test
    fun `capture passes when only some sampled pixels are transparent`() {
        val source = mockk<View>(relaxed = true)
        every { source.width } returns 320
        every { source.height } returns 480
        val captured = mockk<Bitmap>(relaxed = true)

        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888) } returns captured
        mockkConstructor(Canvas::class)

        // The first sample point is transparent, everything after it is opaque.
        var sampleIndex = 0
        val capture = PageCurlCapture { _, _, _ ->
            if (sampleIndex++ == 0) Color.TRANSPARENT else Color.WHITE
        }
        val result = capture.capture(source)

        result shouldBe captured
    }

    @Test
    fun `capture returns null for a zero size source`() {
        val source = mockk<View>(relaxed = true)
        // A relaxed mock returns 0 for width and height.

        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(any<Int>(), any<Int>(), Bitmap.Config.ARGB_8888) } returns
            mockk<Bitmap>(relaxed = true)

        val result = capture.capture(source)

        result shouldBe null
        verify(exactly = 0) {
            Bitmap.createBitmap(any<Int>(), any<Int>(), Bitmap.Config.ARGB_8888)
        }
    }

    @Test
    fun `capture returns null when the bitmap allocation runs out of memory`() {
        val source = mockk<View>(relaxed = true)
        every { source.width } returns 320
        every { source.height } returns 480

        mockkStatic(Bitmap::class)
        every {
            Bitmap.createBitmap(any<Int>(), any<Int>(), Bitmap.Config.ARGB_8888)
        } throws OutOfMemoryError("no memory")

        val result = capture.capture(source)

        result shouldBe null
    }

    @Test
    fun `capture returns null when the source draw fails`() {
        val source = mockk<View>(relaxed = true)
        every { source.width } returns 320
        every { source.height } returns 480
        val captured = mockk<Bitmap>(relaxed = true)

        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888) } returns captured
        mockkConstructor(Canvas::class)
        every { source.draw(any<Canvas>()) } throws IllegalStateException("bitmap is recycled")

        val result = capture.capture(source)

        result shouldBe null
    }

    @Test
    fun `capture returns null when the source draw throws IllegalArgumentException`() {
        val source = mockk<View>(relaxed = true)
        every { source.width } returns 320
        every { source.height } returns 480
        val captured = mockk<Bitmap>(relaxed = true)

        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888) } returns captured
        mockkConstructor(Canvas::class)
        every {
            source.draw(any<Canvas>())
        } throws IllegalArgumentException("Software rendering doesn't support hardware bitmaps")

        val result = capture.capture(source)

        result shouldBe null
    }
}
