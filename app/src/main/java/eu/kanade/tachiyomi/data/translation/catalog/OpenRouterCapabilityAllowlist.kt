package eu.kanade.tachiyomi.data.translation.catalog

object OpenRouterCapabilityAllowlist {

    private val verifiedModels = setOf(
        "google/gemma-4-26b-a4b-it:free",
        "google/gemma-4-31b-it:free",
        "nvidia/nemotron-nano-12b-v2-vl:free",
    )

    fun capabilitiesFor(
        modelId: String,
        minimumOutputTokens: Int?,
        structuredJsonOutput: Boolean,
    ): TranslationModelCapabilities? =
        if (modelId in verifiedModels) {
            TranslationModelCapabilities(
                imageInput = true,
                textOutput = true,
                multilingualOcrAndTranslation = true,
                spatialBounds = true,
                normalizedCoordinates = true,
                originalAndTranslatedFields = true,
                minimumOutputTokens = minimumOutputTokens,
                structuredJsonOutput = structuredJsonOutput,
            )
        } else {
            null
        }
}
