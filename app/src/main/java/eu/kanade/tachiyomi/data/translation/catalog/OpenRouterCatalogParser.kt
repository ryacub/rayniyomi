package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object OpenRouterCatalogParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(responseBody: String, fetchedAtEpochMilliseconds: Long): TranslationModelCatalog {
        val root = runCatching { json.parseToJsonElement(responseBody).jsonObject }
            .getOrElse { error ->
                throw IllegalArgumentException(
                    "OpenRouter catalog response is not a JSON object",
                    error,
                )
            }
        val dataArray = root["data"]?.jsonArray
            ?: throw IllegalArgumentException("OpenRouter catalog response has no model list")

        val models = dataArray.mapNotNull { element ->
            runCatching { parseModel(element.jsonObject) }.getOrNull()
        }

        return TranslationModelCatalog(
            provider = TranslationProvider.OPENROUTER,
            fetchedAtEpochMilliseconds = fetchedAtEpochMilliseconds,
            models = models,
        )
    }

    private fun parseModel(modelObject: JsonObject): TranslationModelEntry {
        val id = modelObject.string("id") ?: throw IllegalArgumentException("Model has no ID")
        val architecture = modelObject["architecture"]?.jsonObject ?: JsonObject(emptyMap())
        val inputModalities = architecture.arrayText("input_modalities")
        val outputModalities = architecture.arrayText("output_modalities")
        val supportedParameters = modelObject.arrayText("supported_parameters")
        val pricing = modelObject["pricing"]?.jsonObject ?: JsonObject(emptyMap())
        val topProvider = modelObject["top_provider"]?.jsonObject ?: JsonObject(emptyMap())
        val promptPrice = pricing.string("prompt")
        val completionPrice = pricing.string("completion")
        val isFree = promptPrice == "0" && completionPrice == "0"

        val outputTokenLimit = topProvider.int("max_completion_tokens")
        val supportsStructuredJson = supportedParameters.contains("response_format")
        val capabilities = OpenRouterCapabilityAllowlist.capabilitiesFor(
            modelId = id,
            minimumOutputTokens = outputTokenLimit,
            structuredJsonOutput = supportsStructuredJson,
        )
            ?: TranslationModelCapabilities(
                imageInput = inputModalities.contains("image"),
                textOutput = outputModalities.contains("text"),
                multilingualOcrAndTranslation = false,
                spatialBounds = false,
                normalizedCoordinates = false,
                originalAndTranslatedFields = false,
                minimumOutputTokens = outputTokenLimit,
                structuredJsonOutput = supportsStructuredJson,
            )

        return TranslationModelEntry(
            id = id,
            displayName = modelObject.string("name") ?: id,
            capabilities = capabilities,
            cost = if (isFree) TranslationModelCost.FREE else TranslationModelCost.UNKNOWN,
            freeTierEligible = if (isFree) true else null,
            stability = TranslationModelStability.UNKNOWN,
            dataTerms = null,
        )
    }

    private fun JsonObject.string(name: String) =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(name: String) =
        this[name]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun JsonObject.arrayText(name: String): List<String> =
        this[name]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
}
