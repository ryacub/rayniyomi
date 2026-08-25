package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PageCurlCaptureTest {

    private val capture = PageCurlCapture()

    private val hardwareBitmaps = mutableSetOf<Bitmap>()

    private fun hardwareBitmapDrawable(): BitmapDrawable {
        val bitmap = mockk<Bitmap>(relaxed = true)
        hardwareBitmaps += bitmap
        return mockk<BitmapDrawable> { every { this@mockk.bitmap } returns bitmap }
    }

    private fun sizedView(): View = mockk(relaxed = true) {
        every { this@mockk.width } returns 100
        every { this@mockk.height } returns 200
    }

    private fun captureTreating() = PageCurlCapture(
        samplePixel = { _, _, _ -> Color.WHITE },
        isHardwareBitmap = { it in hardwareBitmaps },
    )

    @BeforeEach
    fun setUp() {
        hardwareBitmaps.clear()
    }

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

        val capture = PageCurlCapture(samplePixel = { _, _, _ -> Color.WHITE })
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

        val capture = PageCurlCapture(samplePixel = { _, _, _ -> Color.TRANSPARENT })
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

        var sampleIndex = 0
        val capture = PageCurlCapture(
            samplePixel = { _, _, _ ->
                if (sampleIndex++ == 0) Color.TRANSPARENT else Color.WHITE
            },
        )
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

    // This test covers the backstop. The tree walk does not see a hardware
    // bitmap that a custom drawable paints.
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

    @Test
    fun `capture returns null without drawing when the source holds a hardware bitmap`() {
        val source = mockk<ImageView>(relaxed = true)
        every { source.width } returns 100
        every { source.height } returns 200
        every { source.drawable } returns hardwareBitmapDrawable()

        mockkStatic(Bitmap::class)

        val result = captureTreating().capture(source)

        result shouldBe null
        verify(exactly = 0) { source.draw(any<Canvas>()) }
        verify(exactly = 0) { Bitmap.createBitmap(any<Int>(), any<Int>(), any()) }
    }

    @Test
    fun `capture returns null when a nested child holds a hardware bitmap`() {
        val plainChild = sizedView()
        val hardwareChild = mockk<ImageView>(relaxed = true)
        every { hardwareChild.width } returns 100
        every { hardwareChild.height } returns 200
        every { hardwareChild.drawable } returns hardwareBitmapDrawable()

        val source = mockk<ViewGroup>(relaxed = true)
        every { source.width } returns 100
        every { source.height } returns 200
        every { source.childCount } returns 2
        every { source.getChildAt(0) } returns plainChild
        every { source.getChildAt(1) } returns hardwareChild

        mockkStatic(Bitmap::class)

        val result = captureTreating().capture(source)

        result shouldBe null
        verify(exactly = 0) { source.draw(any<Canvas>()) }
        verify(exactly = 0) { Bitmap.createBitmap(any<Int>(), any<Int>(), any()) }
    }

    @Test
    fun `capture returns null when the view background holds a hardware bitmap`() {
        val source = sizedView()
        every { source.background } returns hardwareBitmapDrawable()

        mockkStatic(Bitmap::class)

        val result = captureTreating().capture(source)

        result shouldBe null
        verify(exactly = 0) { source.draw(any<Canvas>()) }
        verify(exactly = 0) { Bitmap.createBitmap(any<Int>(), any<Int>(), any()) }
    }

    @Test
    fun `capture returns null when a layer drawable holds a hardware bitmap`() {
        val layerDrawable = mockk<LayerDrawable> {
            every { numberOfLayers } returns 2
            every { getDrawable(0) } returns mockk<BitmapDrawable>(relaxed = true)
            every { getDrawable(1) } returns hardwareBitmapDrawable()
        }
        val source = mockk<ImageView>(relaxed = true)
        every { source.width } returns 100
        every { source.height } returns 200
        every { source.drawable } returns layerDrawable

        mockkStatic(Bitmap::class)

        val result = captureTreating().capture(source)

        result shouldBe null
        verify(exactly = 0) { source.draw(any<Canvas>()) }
        verify(exactly = 0) { Bitmap.createBitmap(any<Int>(), any<Int>(), any()) }
    }

    @Test
    fun `capture draws when no bitmap in the tree is hardware backed`() {
        val softwareChild = mockk<ImageView>(relaxed = true)
        every { softwareChild.width } returns 100
        every { softwareChild.height } returns 200
        every { softwareChild.drawable } returns mockk<BitmapDrawable>(relaxed = true)

        val source = mockk<ViewGroup>(relaxed = true)
        every { source.width } returns 320
        every { source.height } returns 480
        every { source.childCount } returns 1
        every { source.getChildAt(0) } returns softwareChild

        val captured = mockk<Bitmap>(relaxed = true)
        every { captured.width } returns 320
        every { captured.height } returns 480

        mockkStatic(Bitmap::class)
        every { Bitmap.createBitmap(320, 480, Bitmap.Config.ARGB_8888) } returns captured
        mockkConstructor(Canvas::class)

        val result = captureTreating().capture(source)

        result shouldBe captured
        verify(exactly = 1) { source.draw(any<Canvas>()) }
    }

    @Test
    fun `canCapture reports false for a hardware source and true for a software source`() {
        val hardwareSource = mockk<ImageView>(relaxed = true)
        every { hardwareSource.drawable } returns hardwareBitmapDrawable()
        val softwareSource = mockk<ImageView>(relaxed = true)

        val capture = captureTreating()

        capture.canCapture(hardwareSource) shouldBe false
        capture.canCapture(softwareSource) shouldBe true
    }

    @Test
    fun `capture logs the hardware skip one time for repeated turns`() {
        var logCalls = 0
        val capture = PageCurlCapture(
            samplePixel = { _, _, _ -> Color.WHITE },
            isHardwareBitmap = { true },
            logHardwareSkip = { logCalls++ },
        )
        val source = mockk<ImageView>(relaxed = true)
        every { source.width } returns 100
        every { source.height } returns 200
        every { source.drawable } returns hardwareBitmapDrawable()

        repeat(3) {
            capture.capture(source) shouldBe null
        }

        logCalls shouldBe 1
    }

    @Test
    fun `the hardware config constant is distinct from the software config`() {
        Bitmap.Config.HARDWARE shouldNotBe null
        Bitmap.Config.HARDWARE shouldNotBe Bitmap.Config.ARGB_8888
    }
}
