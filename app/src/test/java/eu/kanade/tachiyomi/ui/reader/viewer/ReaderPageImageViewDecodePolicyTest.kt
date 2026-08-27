package eu.kanade.tachiyomi.ui.reader.viewer

import android.graphics.Bitmap
import android.graphics.RectF
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.util.system.ImageUtil

class ReaderPageImageViewDecodePolicyTest {

    @Test
    fun `displayed image bounds clamp to the holder`() {
        val bounds = normalizedDisplayedImageBounds(
            left = -4.25f,
            top = 12.5f,
            right = 104.75f,
            bottom = 180.25f,
            containerWidth = 100,
            containerHeight = 200,
        )

        assertEquals(0f, bounds?.left)
        assertEquals(12.5f, bounds?.top)
        assertEquals(100f, bounds?.right)
        assertEquals(180.25f, bounds?.bottom)
    }

    @Test
    fun `displayed image bounds reject empty rectangles`() {
        normalizedDisplayedImageBounds(
            left = 40f,
            top = 20f,
            right = 40f,
            bottom = 80f,
            containerWidth = 100,
            containerHeight = 200,
        ) shouldBe null
    }

    @Test
    fun `displayed image bounds include the child offset before clamping`() {
        val imageBounds = RectF().apply {
            left = -10f
            top = 12.5f
            right = 90f
            bottom = 180.25f
        }

        val bounds = displayedImageBoundsInContainer(
            imageBounds = imageBounds,
            imageViewLeft = 8,
            imageViewTop = 4,
            containerWidth = 100,
            containerHeight = 200,
        )

        assertEquals(0f, bounds?.left)
        assertEquals(16.5f, bounds?.top)
        assertEquals(98f, bounds?.right)
        assertEquals(184.25f, bounds?.bottom)
    }

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
