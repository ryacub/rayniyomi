package eu.kanade.presentation.theme.cover

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CoverThemePaletteServiceTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(android.graphics.Color::class)
        every { android.graphics.Color.alpha(any()) } answers {
            (firstArg<Int>() ushr 24) and 0xFF
        }
        every { android.graphics.Color.red(any()) } answers {
            (firstArg<Int>() shr 16) and 0xFF
        }
        every { android.graphics.Color.green(any()) } answers {
            (firstArg<Int>() shr 8) and 0xFF
        }
        every { android.graphics.Color.blue(any()) } answers {
            firstArg<Int>() and 0xFF
        }
        every { android.graphics.Color.rgb(any<Int>(), any<Int>(), any<Int>()) } answers {
            (0xFF shl 24) or (firstArg<Int>() shl 16) or (secondArg<Int>() shl 8) or thirdArg<Int>()
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(android.graphics.Color::class)
    }

    private fun bitmapOf(width: Int, height: Int, pixelAt: (x: Int, y: Int) -> Int): Bitmap {
        val bitmap = mockk<Bitmap>()
        every { bitmap.width } returns width
        every { bitmap.height } returns height
        every { bitmap.getPixel(any(), any()) } answers { pixelAt(firstArg(), secondArg()) }
        return bitmap
    }

    @Test
    fun `hardware bitmap is copied to ARGB_8888 before pixel access`() {
        val hardware = mockk<Bitmap>()
        val software = mockk<Bitmap>()
        every { hardware.config } returns Bitmap.Config.HARDWARE
        every { hardware.copy(Bitmap.Config.ARGB_8888, false) } returns software

        assertSame(software, hardware.asSoftwareBitmap())
        verify { hardware.copy(Bitmap.Config.ARGB_8888, false) }
    }

    @Test
    fun `software bitmap is returned unchanged`() {
        val software = mockk<Bitmap>()
        every { software.config } returns Bitmap.Config.ARGB_8888

        assertSame(software, software.asSoftwareBitmap())
        verify(exactly = 0) { software.copy(Bitmap.Config.ARGB_8888, false) }
    }

    @Test
    fun `failed hardware copy returns null instead of the hardware bitmap`() {
        val hardware = mockk<Bitmap>()
        every { hardware.config } returns Bitmap.Config.HARDWARE
        every { hardware.copy(Bitmap.Config.ARGB_8888, false) } returns null

        assertNull(hardware.asSoftwareBitmap())
    }

    @Test
    fun `opaque pixels are averaged over the sampled grid`() {
        // A 2x2 grid uses step 1, so all four opaque pixels are sampled.
        val bitmap = bitmapOf(2, 2) { x, y ->
            when (x to y) {
                0 to 0 -> 0xFFFF0000.toInt() // red
                1 to 0 -> 0xFF00FF00.toInt() // green
                0 to 1 -> 0xFF0000FF.toInt() // blue
                else -> 0xFFFFFFFF.toInt() // white
            }
        }

        // Each channel sums to 510 over 4 pixels, so each average is 127.
        val expected = Color(0xFF7F7F7F.toInt()).toArgb()
        assertEquals(expected, CoverThemePaletteService.averageOpaqueGridColor(bitmap)?.toArgb())
    }

    @Test
    fun `pixels with alpha below the threshold are excluded from the average`() {
        // Alpha 127 is below the 128 threshold, so only the two white pixels count.
        val dimWhite = 0x7FFFFFFF.toInt()
        val bitmap = bitmapOf(2, 2) { x, y ->
            if ((x + y) % 2 == 0) dimWhite else 0xFFFFFFFF.toInt()
        }

        val expected = Color(0xFFFFFFFF.toInt()).toArgb()
        assertEquals(expected, CoverThemePaletteService.averageOpaqueGridColor(bitmap)?.toArgb())
    }

    @Test
    fun `fully transparent grid returns null`() {
        val bitmap = bitmapOf(2, 2) { _, _ -> 0x00FF0000 }

        assertNull(CoverThemePaletteService.averageOpaqueGridColor(bitmap))
    }
}
