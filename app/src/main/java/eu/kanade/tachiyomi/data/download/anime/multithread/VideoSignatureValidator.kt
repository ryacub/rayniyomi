package eu.kanade.tachiyomi.data.download.anime.multithread

import logcat.LogPriority
import logcat.logcat
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Validates video file signatures for downloaded chunks.
 *
 * This ensures that:
 * - The first chunk starts with a valid video container signature
 * - Downloaded data is actually video content (not HTML error pages, etc.)
 * - Partial downloads can be detected and resumed correctly
 */
object VideoSignatureValidator {

    /**
     * Maximum number of bytes to scan for signatures.
     */
    private const val MAX_SCAN_BYTES = 64 * 1024 // 64 KB

    /**
     * MP4 box signatures.
     * MP4 files typically start with ftyp, moov, or mdat boxes.
     */
    private val MP4_SIGNATURES = listOf(
        "ftyp".toByteArray(),
        "moov".toByteArray(),
        "mdat".toByteArray(),
        "free".toByteArray(),
        "skip".toByteArray(),
    )

    /**
     * MKV/WebM (Matroska) signature.
     * Starts with EBML header: 0x1A 0x45 0xDF 0xA3
     */
    private val MKV_SIGNATURE = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())

    /**
     * WebM signature (subset of Matroska with DocType "webm").
     */
    private val WEBM_SIGNATURE = MKV_SIGNATURE

    /**
     * MPEG-TS signature.
     * Starts with sync byte 0x47 at regular intervals.
     */
    private const val MPEGTS_SYNC_BYTE: Byte = 0x47

    /**
     * AVI signature.
     * Starts with "RIFF....AVI "
     */
    private val AVI_SIGNATURE = "RIFF".toByteArray()
    private val AVI_FORMAT = "AVI ".toByteArray()

    /**
     * Validates that a file contains a valid video signature.
     *
     * For first chunks, validates the start of the file.
     * For non-first chunks, performs basic validation.
     *
     * @param file The file to validate
     * @param isFirstChunk Whether this is the first chunk of the download
     * @return true if the file contains valid video data
     */
    fun validateVideoSignature(file: File, isFirstChunk: Boolean): Boolean {
        if (!file.exists() || file.length() == 0L) {
            return false
        }

        return try {
            RandomAccessFile(file, "r").use { raf ->
                when {
                    isFirstChunk -> validateFirstChunk(raf)
                    else -> validateContinuationChunk(raf)
                }
            }
        } catch (e: IOException) {
            logcat(LogPriority.ERROR) { "Error validating video signature for ${file.name}: ${e.message}" }
            false
        }
    }

    /**
     * Validates the first chunk which should contain container headers.
     */
    private fun validateFirstChunk(raf: RandomAccessFile): Boolean {
        val header = ByteArray(MAX_SCAN_BYTES.coerceAtMost(raf.length().toInt()))
        raf.readFully(header)

        return when {
            containsSignature(header, MP4_SIGNATURES) -> {
                logcat(LogPriority.VERBOSE) { "Detected MP4 signature" }
                true
            }
            header.startsWith(MKV_SIGNATURE) -> {
                logcat(LogPriority.VERBOSE) { "Detected MKV/WebM signature" }
                true
            }
            isMpegTs(header) -> {
                logcat(LogPriority.VERBOSE) { "Detected MPEG-TS signature" }
                true
            }
            isAvi(header) -> {
                logcat(LogPriority.VERBOSE) { "Detected AVI signature" }
                true
            }
            else -> {
                logcat(LogPriority.WARN) { "Unknown video signature" }
                false
            }
        }
    }

    /**
     * Validates a continuation chunk with relaxed checks.
     */
    private fun validateContinuationChunk(raf: RandomAccessFile): Boolean {
        // For non-first chunks, we mainly check:
        // 1. File is not empty
        // 2. File doesn't start with HTML markers (common error case)

        val header = ByteArray(256.coerceAtMost(raf.length().toInt()))
        raf.readFully(header)

        // Check for HTML response (common when authentication fails)
        if (looksLikeHtml(header)) {
            logcat(LogPriority.WARN) { "Chunk appears to be HTML, not video" }
            return false
        }

        return true
    }

    /**
     * Checks if any of the given signatures appear in the data.
     */
    private fun containsSignature(data: ByteArray, signatures: List<ByteArray>): Boolean {
        for (signature in signatures) {
            if (findSignature(data, signature) >= 0) {
                return true
            }
        }
        return false
    }

    /**
     * Finds the position of a signature in the data.
     * MP4 signatures appear after a 4-byte size field.
     */
    private fun findSignature(data: ByteArray, signature: ByteArray): Int {
        for (i in 0..data.size - signature.size - 4) {
            // Check if position i+4 contains the signature (after 4-byte size)
            if (data.sliceArray(i + 4 until i + 4 + signature.size).contentEquals(signature)) {
                return i
            }
        }
        return -1
    }

    /**
     * Checks if data starts with the given signature.
     */
    private fun ByteArray.startsWith(signature: ByteArray): Boolean {
        if (this.size < signature.size) return false
        return this.sliceArray(0 until signature.size).contentEquals(signature)
    }

    /**
     * Checks if the data appears to be MPEG-TS.
     */
    private fun isMpegTs(data: ByteArray): Boolean {
        if (data.size < 188) return false

        // MPEG-TS packets are 188 bytes starting with sync byte 0x47
        return data[0] == MPEGTS_SYNC_BYTE &&
            data[188] == MPEGTS_SYNC_BYTE
    }

    /**
     * Checks if the data appears to be AVI.
     */
    private fun isAvi(data: ByteArray): Boolean {
        if (data.size < 12) return false

        // Check RIFF header
        if (!data.startsWith(AVI_SIGNATURE)) return false

        // Check AVI format at offset 8
        return data.sliceArray(8 until 12).contentEquals(AVI_FORMAT)
    }

    /**
     * Checks if the data looks like HTML (common error response).
     */
    private fun looksLikeHtml(data: ByteArray): Boolean {
        val headerStr = String(data, Charsets.UTF_8).trim().lowercase()

        return headerStr.startsWith("<!doctype") ||
            headerStr.startsWith("<html") ||
            headerStr.startsWith("<head") ||
            headerStr.startsWith("<body") ||
            headerStr.startsWith("<?xml") ||
            headerStr.contains("<html")
    }

    /**
     * Detects the video format from a URL.
     *
     * @param url The URL to analyze
     * @return The detected video format
     */
    fun detectVideoFormat(url: String): VideoFormat {
        val lowercaseUrl = url.lowercase()

        // Remove query parameters for extension detection
        val pathWithoutQuery = lowercaseUrl.substringBefore("?")

        return when {
            pathWithoutQuery.endsWith(".m3u8") ||
                pathWithoutQuery.contains(".m3u8") -> VideoFormat.HLS

            pathWithoutQuery.endsWith(".mpd") ||
                pathWithoutQuery.contains(".mpd") -> VideoFormat.DASH

            pathWithoutQuery.endsWith(".mp4") ||
                pathWithoutQuery.endsWith(".m4v") ||
                pathWithoutQuery.endsWith(".mov") -> VideoFormat.MP4

            pathWithoutQuery.endsWith(".mkv") -> VideoFormat.MKV

            pathWithoutQuery.endsWith(".webm") -> VideoFormat.WEBM

            pathWithoutQuery.endsWith(".ts") -> VideoFormat.MPEG_TS

            pathWithoutQuery.endsWith(".avi") -> VideoFormat.AVI

            // Check for HLS/DASH indicators in URL
            lowercaseUrl.contains("manifest") ||
                lowercaseUrl.contains("playlist") -> VideoFormat.HLS

            else -> VideoFormat.UNKNOWN
        }
    }

    /**
     * Checks if the format supports multi-threaded downloading.
     */
    fun supportsMultiThread(format: VideoFormat): Boolean {
        return when (format) {
            VideoFormat.MP4,
            VideoFormat.MKV,
            VideoFormat.WEBM,
            VideoFormat.AVI,
            -> true

            VideoFormat.HLS,
            VideoFormat.DASH,
            VideoFormat.MPEG_TS,
            VideoFormat.UNKNOWN,
            -> false
        }
    }
}

/**
 * Represents detected video formats.
 */
enum class VideoFormat(val extension: String) {
    MP4("mp4"),
    MKV("mkv"),
    WEBM("webm"),
    HLS("ts"),
    DASH("mp4"),
    MPEG_TS("ts"),
    AVI("avi"),
    UNKNOWN("mp4"),
}
