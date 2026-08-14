package eu.kanade.presentation.theme.cover

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class CoverThemePaletteServiceTest {

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
}
