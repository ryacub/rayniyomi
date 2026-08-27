package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider

data class TranslationModelCapabilities(
    val imageInput: Boolean,
    val textOutput: Boolean,
    val multilingualOcrAndTranslation: Boolean,
    val spatialBounds: Boolean,
    val normalizedCoordinates: Boolean,
    val originalAndTranslatedFields: Boolean,
    val maxOutputTokens: Int?,
    val structuredJsonOutput: Boolean,
    val inputModalities: List<String> = emptyList(),
    val outputModalities: List<String> = emptyList(),
    val supportedParameters: List<String> = emptyList(),
) {
    fun supportsImageTranslation(): Boolean = imageInput && textOutput

    fun supportsAutomaticTranslation(): Boolean = supportsImageTranslation() &&
        maxOutputTokens != null &&
        maxOutputTokens >= MINIMUM_OUTPUT_TOKENS

    companion object {
        const val MINIMUM_OUTPUT_TOKENS = 4_096
    }
}

enum class TranslationModelChoiceType {
    AUTOMATIC,
    PINNED,
}

data class TranslationModelChoice(
    val type: TranslationModelChoiceType,
    val modelId: String? = null,
) {
    init {
        if (type == TranslationModelChoiceType.PINNED) {
            require(!modelId.isNullOrBlank()) { "Pinned model ID must not be blank" }
        } else {
            require(modelId == null) { "Automatic choice must not contain a model ID" }
        }
    }
}

enum class TranslationModelCost {
    FREE,
    PAID,
    UNKNOWN,
}

enum class TranslationModelStability {
    STABLE,
    UNKNOWN,
}

data class TranslationModelEntry(
    val id: String,
    val displayName: String,
    val capabilities: TranslationModelCapabilities,
    val cost: TranslationModelCost,
    val freeTierEligible: Boolean?,
    val stability: TranslationModelStability,
    val dataTerms: String?,
    val pricing: Map<String, String> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "Model ID must not be blank" }
    }
}

data class TranslationModelCatalog(
    val provider: TranslationProvider,
    val fetchedAtEpochMilliseconds: Long,
    val models: List<TranslationModelEntry>,
)
