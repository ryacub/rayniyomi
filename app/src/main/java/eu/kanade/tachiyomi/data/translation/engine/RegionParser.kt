package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.data.translation.InvalidTranslationResponseException
import eu.kanade.tachiyomi.data.translation.NormalizedRect
import eu.kanade.tachiyomi.data.translation.TextRegion
import eu.kanade.tachiyomi.data.translation.TranslationResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared parser for extracting text regions from LLM JSON responses.
 */
object RegionParser {

    fun parse(text: String, json: Json): TranslationResult {
        val jsonArray = extractJsonArray(text)
            ?: throw InvalidTranslationResponseException("Provider response did not contain a JSON array")
        val regions = try {
            json.decodeFromString<List<RegionDto>>(jsonArray)
        } catch (e: Exception) {
            throw InvalidTranslationResponseException("Provider response contained invalid regions")
        }

        if (regions.any { !it.isUsable() }) {
            throw InvalidTranslationResponseException("Provider response contained an unusable region")
        }

        return TranslationResult(
            regions = regions.map { dto ->
                val bounds = NormalizedRect(
                    left = dto.left.coerceIn(0f, 1f),
                    top = dto.top.coerceIn(0f, 1f),
                    right = dto.right.coerceIn(0f, 1f),
                    bottom = dto.bottom.coerceIn(0f, 1f),
                )
                if (bounds.right <= bounds.left || bounds.bottom <= bounds.top) {
                    throw InvalidTranslationResponseException("Provider response contained an out-of-bounds region")
                }
                TextRegion(
                    bounds = bounds,
                    originalText = dto.original,
                    translatedText = dto.translated,
                )
            },
        )
    }

    private fun extractJsonArray(text: String): String? {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return null
        return text.substring(start, end + 1)
    }

    @Serializable
    data class RegionDto(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val original: String,
        val translated: String,
    ) {
        fun isUsable(): Boolean =
            left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite() &&
                right > left && bottom > top &&
                original.isNotBlank() && translated.isNotBlank()
    }
}

/**
 * Shared response format for OpenAI-compatible APIs (OpenAI, OpenRouter).
 */
@Serializable
data class OpenAICompatResponse(
    val choices: List<OpenAICompatChoice> = emptyList(),
)

@Serializable
data class OpenAICompatChoice(
    val message: OpenAICompatMessage? = null,
)

@Serializable
data class OpenAICompatMessage(
    val content: String = "",
)
