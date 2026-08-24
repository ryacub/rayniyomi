package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses Claude model list responses from GET /v1/models.
 *
 * Vision capability comes from a documented policy, not API truth: every model in the
 * list accepts image input and produces text output. The policy is re-verified against
 * live provider documentation at ticket close.
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
                imageInput = true,
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
}
