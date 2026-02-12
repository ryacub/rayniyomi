package eu.kanade.tachiyomi.data.translation.engine

import android.graphics.Bitmap

/**
 * Utility for detecting image format from byte headers.
 * Single source of truth for format detection across the translation pipeline.
 */
object ImageFormatUtil {

    fun detectMediaType(imageBytes: ByteArray): String {
        return when {
            isPng(imageBytes) -> "image/png"
            isWebP(imageBytes) -> "image/webp"
            else -> "image/jpeg"
        }
    }

    fun detectExtension(imageBytes: ByteArray): String {
        return when {
            isPng(imageBytes) -> "png"
            isWebP(imageBytes) -> "webp"
            else -> "jpg"
        }
    }

    fun detectCompressFormat(imageBytes: ByteArray): Bitmap.CompressFormat {
        return when {
            isPng(imageBytes) -> Bitmap.CompressFormat.PNG
            isWebP(imageBytes) -> Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    private fun isPng(imageBytes: ByteArray): Boolean {
        return imageBytes.size >= 4 &&
            imageBytes[0] == 0x89.toByte() &&
            imageBytes[1] == 0x50.toByte() &&
            imageBytes[2] == 0x4E.toByte() &&
            imageBytes[3] == 0x47.toByte()
    }

    private fun isWebP(imageBytes: ByteArray): Boolean {
        // RIFF header (bytes 0-3) + WEBP marker (bytes 8-11)
        return imageBytes.size >= 12 &&
            imageBytes[0] == 0x52.toByte() &&
            imageBytes[1] == 0x49.toByte() &&
            imageBytes[2] == 0x46.toByte() &&
            imageBytes[3] == 0x46.toByte() &&
            imageBytes[8] == 0x57.toByte() &&
            imageBytes[9] == 0x45.toByte() &&
            imageBytes[10] == 0x42.toByte() &&
            imageBytes[11] == 0x50.toByte()
    }
}
