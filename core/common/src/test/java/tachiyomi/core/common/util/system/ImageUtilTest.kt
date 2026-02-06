package tachiyomi.core.common.util.system

import android.graphics.BitmapFactory
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.InputStream

class ImageUtilTest {

    @BeforeEach
    fun setUp() {
        mockkStatic(BitmapFactory::class)
        mockkStatic(android.content.res.Resources::class)
        val mockResources = io.mockk.mockk<android.content.res.Resources>()
        val mockDisplayMetrics = android.util.DisplayMetrics().apply {
            widthPixels = 1080
            heightPixels = 1920
        }
        io.mockk.every { android.content.res.Resources.getSystem() } returns mockResources
        io.mockk.every { mockResources.displayMetrics } returns mockDisplayMetrics
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(BitmapFactory::class)
        unmockkStatic(android.content.res.Resources::class)
    }

    @Test
    fun `isTallImage returns true for images with ratio greater than 3`() {
        mockBitmapDimensions(width = 1000, height = 3001)
        val source = Buffer()

        ImageUtil.isTallImage(source) shouldBe true
    }

    @Test
    fun `isTallImage returns false for images with 3-1 ratio exactly`() {
        mockBitmapDimensions(width = 1000, height = 3000)
        val source = Buffer()

        ImageUtil.isTallImage(source) shouldBe false
    }

    @Test
    fun `isTallImage returns false for square images`() {
        mockBitmapDimensions(width = 1000, height = 1000)
        val source = Buffer()

        ImageUtil.isTallImage(source) shouldBe false
    }

    @Test
    fun `isTallImage returns false for wide images`() {
        mockBitmapDimensions(width = 3000, height = 1000)
        val source = Buffer()

        ImageUtil.isTallImage(source) shouldBe false
    }

    private fun mockBitmapDimensions(width: Int, height: Int) {
        every { 
            BitmapFactory.decodeStream(any<InputStream>(), any(), any()) 
        } answers {
            val options = thirdArg<BitmapFactory.Options>()
            options.outWidth = width
            options.outHeight = height
            null
        }
    }
}