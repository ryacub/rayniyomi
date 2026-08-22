package eu.kanade.tachiyomi.data.translation.catalog

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TranslationModelCatalogFilterTest {

    private val compatibleCapabilities = TranslationModelCapabilities(
        imageInput = true,
        textOutput = true,
        multilingualOcrAndTranslation = true,
        spatialBounds = true,
        normalizedCoordinates = true,
        originalAndTranslatedFields = true,
        minimumOutputTokens = 4_096,
        structuredJsonOutput = true,
    )

    @Test
    fun `keeps only models with every required capability`() {
        val missingImageInput = compatibleCapabilities.copy(imageInput = false)
        val belowTokenLimit = compatibleCapabilities.copy(minimumOutputTokens = 1_024)

        val result = TranslationModelCatalogFilter.filter(
            listOf(
                entry("compatible", compatibleCapabilities),
                entry("missing-image-input", missingImageInput),
                entry("below-limit", belowTokenLimit),
            ),
        )

        result.map { it.id } shouldBe listOf("compatible")
    }

    @Test
    fun `keeps only free models`() {
        val paid = entry("paid", compatibleCapabilities).copy(cost = TranslationModelCost.PAID)
        val unknown = entry("unknown", compatibleCapabilities).copy(cost = TranslationModelCost.UNKNOWN)

        TranslationModelCatalogFilter.filter(listOf(paid, unknown)) shouldBe emptyList()
    }

    private fun entry(id: String, capabilities: TranslationModelCapabilities) =
        TranslationModelEntry(
            id = id,
            displayName = id,
            capabilities = capabilities,
            cost = TranslationModelCost.FREE,
            freeTierEligible = true,
            stability = TranslationModelStability.UNKNOWN,
            dataTerms = null,
        )
}
