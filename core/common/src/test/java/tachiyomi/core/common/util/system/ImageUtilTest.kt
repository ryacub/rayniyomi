package tachiyomi.core.common.util.system

import io.kotest.matchers.shouldBe
import okio.Buffer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import java.io.ByteArrayInputStream

@Execution(ExecutionMode.CONCURRENT)
class ImageUtilTest {

    @Test
    fun `isTallImage returns true for very tall images`() {
        // Create a mock tall image (3000x1000 = 3:1 ratio)
        val tallImageData = createMockImageData(width = 1000, height = 3000)
        val source = Buffer().readFrom(ByteArrayInputStream(tallImageData))

        ImageUtil.isTallImage(source) shouldBe true
    }

    @Test
    fun `isTallImage returns false for square images`() {
        // Create a mock square image (1000x1000 = 1:1 ratio)
        val squareImageData = createMockImageData(width = 1000, height = 1000)
        val source = Buffer().readFrom(ByteArrayInputStream(squareImageData))

        ImageUtil.isTallImage(source) shouldBe false
    }

    @Test
    fun `isTallImage returns false for wide images`() {
        // Create a mock wide image (3000x1000 = 0.33:1 ratio)
        val wideImageData = createMockImageData(width = 3000, height = 1000)
        val source = Buffer().readFrom(ByteArrayInputStream(wideImageData))

        ImageUtil.isTallImage(source) shouldBe false
    }

    @Test
    fun `isTallImage returns false for images with 3-1 ratio exactly`() {
        // Create an image with exactly 3:1 ratio (should return false since ratio must be > 3)
        val exactRatioImageData = createMockImageData(width = 1000, height = 3000)
        val source = Buffer().readFrom(ByteArrayInputStream(exactRatioImageData))

        // Note: 3000/1000 = 3.0, so this should be false (needs to be > 3)
        ImageUtil.isTallImage(source) shouldBe false
    }

    @Test
    fun `isTallImage returns true for images with ratio greater than 3`() {
        // Create an image with 4:1 ratio
        val veryTallImageData = createMockImageData(width = 1000, height = 4000)
        val source = Buffer().readFrom(ByteArrayInputStream(veryTallImageData))

        ImageUtil.isTallImage(source) shouldBe true
    }

    /**
     * Creates a minimal valid JPEG image with the specified dimensions.
     * This is a simplified mock that won't actually render but has valid headers.
     */
    private fun createMockImageData(width: Int, height: Int): ByteArray {
        // JPEG SOI marker
        val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())

        // APP0 marker (JFIF)
        val app0 = byteArrayOf(
            0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10, // Length
            0x4A, 0x46, 0x49, 0x46, 0x00, // JFIF\0
            0x01, 0x01, // Version
            0x00, // Units
            0x00, 0x01, // X density
            0x00, 0x01, // Y density
            0x00, 0x00  // Thumbnail
        )

        // DQT (Define Quantization Table)
        val dqt = byteArrayOf(
            0xFF.toByte(), 0xDB.toByte(),
            0x00, 0x43, // Length
            0x00        // Precision and table ID
        ) + ByteArray(64) { 0x10.toByte() } // Table data

        // SOF0 (Start Of Frame - Baseline DCT)
        val sof0 = byteArrayOf(
            0xFF.toByte(), 0xC0.toByte(),
            0x00, 0x0B, // Length
            0x08,       // Precision
            (height shr 8).toByte(), (height and 0xFF).toByte(), // Height
            (width shr 8).toByte(), (width and 0xFF).toByte(),   // Width
            0x01,       // Components
            0x01, 0x11, 0x00 // Component info
        )

        // DHT (Define Huffman Table)
        val dht = byteArrayOf(
            0xFF.toByte(), 0xC4.toByte(),
            0x00, 0x1F, // Length
            0x00        // Table class and ID
        ) + ByteArray(28) { 0x00.toByte() }

        // SOS (Start Of Scan)
        val sos = byteArrayOf(
            0xFF.toByte(), 0xDA.toByte(),
            0x00, 0x08, // Length
            0x01,       // Components
            0x01, 0x00, // Component info
            0x00, 0x3F, 0x00 // Spectral selection
        )

        // Image data (minimal)
        val imageData = byteArrayOf(0x00)

        // EOI (End Of Image)
        val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

        return soi + app0 + dqt + sof0 + dht + sos + imageData + eoi
    }
}
