package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object OpenRouterCatalogParser {

    fun parse(responseBody: String, fetchedAtEpochMilliseconds: Long): TranslationModelCatalog {
        val root = catalogRootObject(responseBody, "OpenRouter")
        val dataArray = root["data"]?.jsonArray
            ?: throw IllegalArgumentException("OpenRouter catalog response has no model list")

        val models = dataArray.mapNotNull { element ->
            runCatching { parseModel(element.jsonObject) }.getOrNull()
        }
        if (dataArray.isNotEmpty() && models.isEmpty()) {
            throw IllegalArgumentException("OpenRouter catalog response has no valid models")
        }

        return TranslationModelCatalog(
            provider = TranslationProvider.OPENROUTER,
            fetchedAtEpochMilliseconds = fetchedAtEpochMilliseconds,
            models = models,
        )
    }

    private fun parseModel(modelObject: JsonObject): TranslationModelEntry {
        val id = modelObject.string("id") ?: throw IllegalArgumentException("Model has no ID")
        val architecture = modelObject["architecture"] as? JsonObject ?: JsonObject(emptyMap())
        val inputModalities = architecture.textArray("input_modalities")
        val outputModalities = architecture.textArray("output_modalities")
        val supportedParameters = modelObject.textArray("supported_parameters")
        val pricing = modelObject.stringMap("pricing")
        val topProvider = modelObject["top_provider"] as? JsonObject ?: JsonObject(emptyMap())
        val cost = pricing.cost()

        val outputTokenLimit = topProvider.int("max_completion_tokens")
        val capabilities = TranslationModelCapabilities(
            imageInput = inputModalities.contains("image"),
            textOutput = outputModalities.contains("text"),
            multilingualOcrAndTranslation = false,
            spatialBounds = false,
            normalizedCoordinates = false,
            originalAndTranslatedFields = false,
            maxOutputTokens = outputTokenLimit,
            structuredJsonOutput = supportedParameters.contains("response_format"),
            inputModalities = inputModalities,
            outputModalities = outputModalities,
            supportedParameters = supportedParameters,
        )

        return TranslationModelEntry(
            id = id,
            displayName = runCatching { modelObject.string("name") }.getOrNull() ?: id,
            capabilities = capabilities,
            cost = cost,
            freeTierEligible = if (cost == TranslationModelCost.FREE) true else null,
            stability = TranslationModelStability.UNKNOWN,
            dataTerms = null,
            pricing = pricing,
        )
    }

    private fun JsonObject.int(name: String) =
        runCatching { this[name]?.jsonPrimitive?.contentOrNull?.toIntOrNull() }.getOrNull()

    private fun JsonObject.stringMap(name: String): Map<String, String> =
        (this[name] as? JsonObject)?.mapNotNull { (key, value) ->
            val primitiveValue = runCatching { value.jsonPrimitive.contentOrNull }.getOrNull()
            key to (primitiveValue ?: value.toString())
        }?.toMap().orEmpty()

    private fun Map<String, String>.cost(): TranslationModelCost {
        val prompt = this["prompt"]?.toBigDecimalOrNull()
        val completion = this["completion"]?.toBigDecimalOrNull()
        val prices = values.map { it.toBigDecimalOrNull() }
        return when {
            prompt == null || completion == null || prices.any { it == null } -> TranslationModelCost.UNKNOWN
            prices.any { it?.signum() ?: 0 < 0 } -> TranslationModelCost.UNKNOWN
            prices.any { it?.signum() ?: 0 > 0 } -> TranslationModelCost.PAID
            else -> TranslationModelCost.FREE
        }
    }
}
