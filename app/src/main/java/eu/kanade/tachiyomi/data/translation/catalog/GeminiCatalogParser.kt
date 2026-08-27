package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses Google Gemini model list responses from v1beta models.list.
 *
 * The response may carry nextPageToken; this parser ignores it because the first page
 * covers every currently usable generation model. Revisit when Google documents a need
 * to page past it.
 *
 * Vision capability comes from a documented policy, not API truth: every model that
 * supports generateContent accepts image input and produces text output. The policy is
 * re-verified against live provider documentation at ticket close.
 */
object GeminiCatalogParser {

    private const val MODEL_NAME_PREFIX = "models/"

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(responseBody: String, fetchedAtEpochMilliseconds: Long): TranslationModelCatalog {
        val root = runCatching { json.parseToJsonElement(responseBody).jsonObject }
            .getOrElse { error ->
                throw IllegalArgumentException(
                    "Gemini catalog response is not a JSON object",
                    error,
                )
            }
        val modelArray = root["models"]?.let { it as? JsonArray }
            ?: throw IllegalArgumentException("Gemini catalog response has no model list")

        val models = modelArray.mapNotNull { element ->
            runCatching { parseModel(element.jsonObject) }.getOrNull()
        }

        return TranslationModelCatalog(
            provider = TranslationProvider.GOOGLE,
            fetchedAtEpochMilliseconds = fetchedAtEpochMilliseconds,
            models = models,
        )
    }

    private fun parseModel(modelObject: JsonObject): TranslationModelEntry? {
        val fullName = modelObject.string("name") ?: throw IllegalArgumentException("Model has no ID")
        if (!modelObject.generationMethods().contains("generateContent")) return null

        return TranslationModelEntry(
            id = fullName.removePrefix(MODEL_NAME_PREFIX),
            displayName = modelObject.string("displayName") ?: fullName,
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

    private fun JsonObject.generationMethods(): List<String> =
        this["supportedGenerationMethods"]
            ?.let { it as? JsonArray }
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?: emptyList()

    private fun JsonObject.string(name: String) =
        this[name]?.jsonPrimitive?.contentOrNull
}
