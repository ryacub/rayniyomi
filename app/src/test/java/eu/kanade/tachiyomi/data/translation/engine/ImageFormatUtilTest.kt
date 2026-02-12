package eu.kanade.tachiyomi.data.translation.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImageFormatUtilTest {

    @Test
    fun `detects JPEG format`() {
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)
        assertEquals("image/jpeg", ImageFormatUtil.detectMediaType(jpegBytes))
        assertEquals("jpg", ImageFormatUtil.detectExtension(jpegBytes))
    }

    @Test
    fun `detects PNG format`() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        assertEquals("image/png", ImageFormatUtil.detectMediaType(pngBytes))
        assertEquals("png", ImageFormatUtil.detectExtension(pngBytes))
    }

    @Test
    fun `detects WebP format with full RIFF+WEBP header`() {
        // RIFF....WEBP - proper WebP magic bytes
        val webpBytes = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), // RIFF
            0x00, 0x00, 0x00, 0x00, // file size (don't care)
            0x57.toByte(), 0x45.toByte(), 0x42.toByte(), 0x50.toByte(), // WEBP
        )
        assertEquals("image/webp", ImageFormatUtil.detectMediaType(webpBytes))
        assertEquals("webp", ImageFormatUtil.detectExtension(webpBytes))
    }

    @Test
    fun `RIFF without WEBP marker defaults to JPEG`() {
        // RIFF header but not WEBP (e.g., WAV file)
        val riffBytes = byteArrayOf(
            0x52.toByte(), 0x49.toByte(), 0x46.toByte(), 0x46.toByte(), // RIFF
            0x00, 0x00, 0x00, 0x00,
            0x57.toByte(), 0x41.toByte(), 0x56.toByte(), 0x45.toByte(), // WAVE
        )
        assertEquals("image/jpeg", ImageFormatUtil.detectMediaType(riffBytes))
    }

    @Test
    fun `defaults to JPEG for unknown format`() {
        val unknownBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertEquals("image/jpeg", ImageFormatUtil.detectMediaType(unknownBytes))
    }

    @Test
    fun `defaults to JPEG for empty bytes`() {
        assertEquals("image/jpeg", ImageFormatUtil.detectMediaType(byteArrayOf()))
    }

    @Test
    fun `defaults to JPEG for too few bytes`() {
        val shortBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        assertEquals("image/jpeg", ImageFormatUtil.detectMediaType(shortBytes))
    }
}
