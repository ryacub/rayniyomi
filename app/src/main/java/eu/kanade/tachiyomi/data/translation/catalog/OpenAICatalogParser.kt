package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses OpenAI model list responses from GET /v1/models.
 *
 * The endpoint returns one full list and has no pagination field.
 *
 * Vision capability comes from a documented policy, not API truth: chat-capable model
 * families accept image input, except the text-only models in [TEXT_ONLY_DENYLIST],
 * which this parser drops outright. The policy is re-verified against live provider
 * documentation at ticket close.
 */
object OpenAICatalogParser {

    private val CHAT_FAMILY_PREFIXES = listOf(
        Regex("^gpt-"),
        Regex("^o\\d"),
        Regex("^chatgpt"),
    )

    private val TEXT_ONLY_DENYLIST = listOf(
        Regex("^gpt-3\\.5-turbo"),
        Regex("^o1-mini"),
        Regex("^o3-mini"),
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(responseBody: String, fetchedAtEpochMilliseconds: Long): TranslationModelCatalog {
        val root = runCatching { json.parseToJsonElement(responseBody).jsonObject }
            .getOrElse { error ->
                throw IllegalArgumentException(
                    "OpenAI catalog response is not a JSON object",
                    error,
                )
            }
        val dataArray = root["data"]?.jsonArray
            ?: throw IllegalArgumentException("OpenAI catalog response has no model list")

        val models = dataArray.mapNotNull { element ->
            runCatching { parseModel(element.jsonObject) }.getOrNull()
        }

        return TranslationModelCatalog(
            provider = TranslationProvider.OPENAI,
            fetchedAtEpochMilliseconds = fetchedAtEpochMilliseconds,
            models = models,
        )
    }

    private fun parseModel(modelObject: JsonObject): TranslationModelEntry? {
        val id = modelObject.string("id") ?: throw IllegalArgumentException("Model has no ID")
        if (!isChatCapable(id) || isTextOnly(id)) return null

        return TranslationModelEntry(
            id = id,
            displayName = id,
            capabilities = TranslationModelCapabilities(
                imageInput = true,
                textOutput = true,
                multilingualOcrAndTranslation = false,
                spatialBounds = false,
                normalizedCoordinates = false,
                originalAndTranslatedFields = false,
                maxOutputTokens = null,
                structuredJsonOutput = false,
            ),
            cost = TranslationModelCost.PAID,
            freeTierEligible = null,
            stability = TranslationModelStability.UNKNOWN,
            dataTerms = null,
        )
    }

    private fun isChatCapable(id: String): Boolean =
        CHAT_FAMILY_PREFIXES.any { it.containsMatchIn(id) }

    private fun isTextOnly(id: String): Boolean =
        TEXT_ONLY_DENYLIST.any { it.containsMatchIn(id) }

    private fun JsonObject.string(name: String) =
        this[name]?.jsonPrimitive?.contentOrNull
}
