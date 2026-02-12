package eu.kanade.tachiyomi.data.translation.engine

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
        val regions = try {
            json.decodeFromString<List<RegionDto>>(jsonArray)
        } catch (e: Exception) {
            emptyList()
        }

        return TranslationResult(
            regions = regions.map { dto ->
                TextRegion(
                    bounds = NormalizedRect(
                        left = dto.left.coerceIn(0f, 1f),
                        top = dto.top.coerceIn(0f, 1f),
                        right = dto.right.coerceIn(0f, 1f),
                        bottom = dto.bottom.coerceIn(0f, 1f),
                    ),
                    originalText = dto.original,
                    translatedText = dto.translated,
                )
            },
        )
    }

    private fun extractJsonArray(text: String): String {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) return "[]"
        return text.substring(start, end + 1)
    }

    @Serializable
    data class RegionDto(
        val left: Float = 0f,
        val top: Float = 0f,
        val right: Float = 0f,
        val bottom: Float = 0f,
        val original: String = "",
        val translated: String = "",
    )
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
