package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses Claude model list responses from GET /v1/models.
 *
 * Vision capability comes from the API when present: `capabilities.image_input.supported`
 * sets [TranslationModelCapabilities.imageInput]. When the entry has no capabilities
 * object, no `image_input` key, or no `supported` boolean, image input falls back to
 * true by documented policy; text output is always true by the same policy. The policy
 * is re-verified against live provider documentation at ticket close.
 */
object ClaudeCatalogParser {

    data class Page(
        val models: List<TranslationModelEntry>,
        val hasMore: Boolean,
        val lastId: String?,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(responseBody: String, fetchedAtEpochMilliseconds: Long): TranslationModelCatalog =
        buildCatalog(parsePage(responseBody).models, fetchedAtEpochMilliseconds)

    fun parsePage(responseBody: String): Page {
        val root = runCatching { json.parseToJsonElement(responseBody).jsonObject }
            .getOrElse { error ->
                throw IllegalArgumentException(
                    "Claude catalog response is not a JSON object",
                    error,
                )
            }
        val dataArray = root["data"]?.jsonArray
            ?: throw IllegalArgumentException("Claude catalog response has no model list")

        val models = dataArray.mapNotNull { element ->
            runCatching { parseModel(element.jsonObject) }.getOrNull()
        }

        return Page(
            models = models,
            hasMore = root["has_more"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
            lastId = root.string("last_id"),
        )
    }

    private fun buildCatalog(models: List<TranslationModelEntry>, fetchedAtEpochMilliseconds: Long) =
        TranslationModelCatalog(
            provider = TranslationProvider.CLAUDE,
            fetchedAtEpochMilliseconds = fetchedAtEpochMilliseconds,
            models = models,
        )

    private fun parseModel(modelObject: JsonObject): TranslationModelEntry {
        val id = modelObject.string("id") ?: throw IllegalArgumentException("Model has no ID")

        return TranslationModelEntry(
            id = id,
            displayName = modelObject.string("display_name") ?: id,
            capabilities = TranslationModelCapabilities(
                imageInput = modelObject.imageInputSupported(),
                textOutput = true,
                multilingualOcrAndTranslation = false,
                spatialBounds = false,
                normalizedCoordinates = false,
                originalAndTranslatedFields = false,
                minimumOutputTokens = null,
                structuredJsonOutput = false,
            ),
            cost = TranslationModelCost.PAID,
            freeTierEligible = null,
            stability = TranslationModelStability.UNKNOWN,
            dataTerms = null,
        )
    }

    private fun JsonObject.string(name: String) =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.imageInputSupported(): Boolean =
        runCatching {
            val capabilities = this["capabilities"]?.jsonObject ?: return true
            val imageInput = capabilities["image_input"]?.jsonObject ?: return true
            imageInput["supported"]?.jsonPrimitive?.booleanOrNull ?: return true
        }.getOrDefault(true)
}
