package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.data.download.anime.multithread.VideoFormat
import eu.kanade.tachiyomi.data.download.anime.multithread.VideoSignatureValidator
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File

class VideoSignatureValidatorTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `validateVideoSignature returns true for valid MP4 signature`() {
        // ftyp box signature: "ftyp" followed by brand
        val mp4Bytes = byteArrayOf(
            0x00, 0x00, 0x00, 0x20, // box size (32 bytes)
            0x66, 0x74, 0x79, 0x70, // "ftyp"
            0x69, 0x73, 0x6F, 0x6D, // "isom" brand
        )
        val file = File(tempDir, "test.mp4")
        file.writeBytes(mp4Bytes)

        val result = VideoSignatureValidator.validateVideoSignature(file, true)

        result shouldBe true
    }

    @Test
    fun `validateVideoSignature returns true for valid MP4 with mdat first`() {
        // Some MP4s start with mdat before moov
        val mp4Bytes = byteArrayOf(
            0x00,
            0x00,
            0x00,
            0x08, // box size
            0x6D,
            0x64,
            0x61,
            0x74, // "mdat"
        )
        val file = File(tempDir, "test_mdat.mp4")
        file.writeBytes(mp4Bytes)

        val result = VideoSignatureValidator.validateVideoSignature(file, false)

        result shouldBe true
    }

    @Test
    fun `validateVideoSignature returns true for valid MP4 with moov`() {
        val mp4Bytes = byteArrayOf(
            0x00,
            0x00,
            0x00,
            0x10, // box size
            0x6D,
            0x6F,
            0x6F,
            0x76, // "moov"
        )
        val file = File(tempDir, "test_moov.mp4")
        file.writeBytes(mp4Bytes)

        val result = VideoSignatureValidator.validateVideoSignature(file, false)

        result shouldBe true
    }

    @Test
    fun `validateVideoSignature returns false for invalid signature`() {
        val invalidBytes = byteArrayOf(
            0x00,
            0x00,
            0x00,
            0x00, // invalid
            0x00,
            0x00,
            0x00,
            0x00,
        )
        val file = File(tempDir, "test_invalid.mp4")
        file.writeBytes(invalidBytes)

        val result = VideoSignatureValidator.validateVideoSignature(file, true)

        result shouldBe false
    }

    @Test
    fun `validateVideoSignature returns false for empty file`() {
        val file = File(tempDir, "empty.mp4")
        file.writeBytes(byteArrayOf())

        val result = VideoSignatureValidator.validateVideoSignature(file, true)

        result shouldBe false
    }

    @Test
    fun `validateVideoSignature returns false for non-existent file`() {
        val file = File(tempDir, "nonexistent.mp4")

        val result = VideoSignatureValidator.validateVideoSignature(file, true)

        result shouldBe false
    }

    @Test
    fun `validateVideoSignature handles ftyp at offset`() {
        // Some files may have a small header before ftyp
        val bytes = byteArrayOf(
            0x00, 0x00, 0x00, 0x04, // 4 byte padding
            0x00, 0x00, 0x00, 0x20, // box size
            0x66, 0x74, 0x79, 0x70, // "ftyp"
        )
        val file = File(tempDir, "offset_ftyp.mp4")
        file.writeBytes(bytes)

        val result = VideoSignatureValidator.validateVideoSignature(file, true)

        result shouldBe true
    }

    @ParameterizedTest
    @CsvSource(
        "http://example.com/video.mp4, MP4",
        "http://example.com/video.mkv, MKV",
        "http://example.com/video.webm, WEBM",
        "http://example.com/video.m3u8, HLS",
        "http://example.com/master.m3u8, HLS",
        "http://example.com/video.mpd, DASH",
        "http://example.com/manifest.mpd, DASH",
        "http://example.com/video.unknown, UNKNOWN",
        "http://example.com/video, UNKNOWN",
    )
    fun `detectVideoFormat returns correct format`(url: String, expectedFormat: String) {
        val result = VideoSignatureValidator.detectVideoFormat(url)

        result shouldBe VideoFormat.valueOf(expectedFormat)
    }

    @Test
    fun `detectVideoFormat is case insensitive`() {
        val result1 = VideoSignatureValidator.detectVideoFormat("http://example.com/video.MP4")
        val result2 = VideoSignatureValidator.detectVideoFormat("http://example.com/video.Mp4")
        val result3 = VideoSignatureValidator.detectVideoFormat("http://example.com/video.mp4")

        result1 shouldBe VideoFormat.MP4
        result2 shouldBe VideoFormat.MP4
        result3 shouldBe VideoFormat.MP4
    }

    @Test
    fun `detectVideoFormat handles query parameters`() {
        val result = VideoSignatureValidator.detectVideoFormat(
            "http://example.com/video.mp4?token=abc&expires=123",
        )

        result shouldBe VideoFormat.MP4
    }

    @Test
    fun `detectVideoFormat handles URLs without extension in path`() {
        val result = VideoSignatureValidator.detectVideoFormat(
            "http://example.com/stream?id=123&type=mp4",
        )

        // Should fall back to UNKNOWN if no extension in path
        result shouldBe VideoFormat.UNKNOWN
    }
}
