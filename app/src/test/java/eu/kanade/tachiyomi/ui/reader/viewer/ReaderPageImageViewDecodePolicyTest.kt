package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.Bitmap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.util.system.ImageUtil

class ReaderPageImageViewDecodePolicyTest {

    private fun analysis(
        isTallImage: Boolean,
        canUseHardwareBitmap: Boolean,
    ): ImageUtil.ImageAnalysis = ImageUtil.ImageAnalysis(isTallImage, canUseHardwareBitmap)

    @Test
    fun `non-webtoon never uses hardware so page curl capture stays drawable`() {
        for (pref in listOf(false, true)) {
            for (tall in listOf(false, true)) {
                for (hw in listOf(false, true)) {
                    val config = readerDecodeConfig(
                        isWebtoon = false,
                        alwaysDecodeLongStripWithSSIV = pref,
                        analysis = analysis(tall, hw),
                    )
                    assertEquals(Bitmap.Config.ARGB_8888, config)
                }
            }
        }
    }

    @Test
    fun `webtoon with pref on follows canUseHardwareBitmap`() {
        assertEquals(
            Bitmap.Config.HARDWARE,
            readerDecodeConfig(true, true, analysis(isTallImage = false, canUseHardwareBitmap = true)),
        )
        assertEquals(
            Bitmap.Config.ARGB_8888,
            readerDecodeConfig(true, true, analysis(isTallImage = false, canUseHardwareBitmap = false)),
        )
        assertEquals(
            Bitmap.Config.HARDWARE,
            readerDecodeConfig(true, true, analysis(isTallImage = true, canUseHardwareBitmap = true)),
        )
        assertEquals(
            Bitmap.Config.ARGB_8888,
            readerDecodeConfig(true, true, analysis(isTallImage = true, canUseHardwareBitmap = false)),
        )
    }

    @Test
    fun `webtoon with pref off and tall image follows canUseHardwareBitmap`() {
        assertEquals(
            Bitmap.Config.HARDWARE,
            readerDecodeConfig(true, false, analysis(isTallImage = true, canUseHardwareBitmap = true)),
        )
        assertEquals(
            Bitmap.Config.ARGB_8888,
            readerDecodeConfig(true, false, analysis(isTallImage = true, canUseHardwareBitmap = false)),
        )
    }

    @Test
    fun `webtoon with pref off and not tall takes the Coil path and returns ARGB_8888`() {
        for (hw in listOf(false, true)) {
            val config = readerDecodeConfig(
                isWebtoon = true,
                alwaysDecodeLongStripWithSSIV = false,
                analysis = analysis(isTallImage = false, canUseHardwareBitmap = hw),
            )
            assertEquals(Bitmap.Config.ARGB_8888, config)
        }
    }
}
