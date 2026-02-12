package eu.kanade.tachiyomi.data.translation

/**
 * Result of translating a manga page image via a vision LLM.
 */
data class TranslationResult(
    val regions: List<TextRegion>,
)

/**
 * Normalized bounding box (0-1 coords relative to image dimensions).
 * Plain data class to avoid Android framework dependency in unit tests.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * A detected text region on a manga page.
 *
 * @param bounds Normalized bounding box (0-1 coords relative to image dimensions).
 * @param originalText The original text detected in this region.
 * @param translatedText The translated text for this region.
 */
data class TextRegion(
    val bounds: NormalizedRect,
    val originalText: String,
    val translatedText: String,
)

/**
 * Pluggable backend for detecting and translating text on manga page images.
 */
interface TranslationEngine {

    /**
     * Detect text regions on the image and return translations.
     *
     * @param imageBytes Raw image bytes (JPEG/PNG/WebP).
     * @param targetLang BCP-47 language code for the target translation (e.g. "en").
     * @return [TranslationResult] containing all detected and translated regions.
     */
    suspend fun detectAndTranslate(
        imageBytes: ByteArray,
        targetLang: String,
    ): TranslationResult
}
