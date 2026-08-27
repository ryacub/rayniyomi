package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
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
        maxOutputTokens = 4_096,
        structuredJsonOutput = true,
    )

    @Test
    fun `keeps image input and text output models for the picker`() {
        val missingImageInput = compatibleCapabilities.copy(imageInput = false)
        val missingTextOutput = compatibleCapabilities.copy(textOutput = false)

        val result = TranslationModelCatalogFilter.filter(
            listOf(
                entry("compatible", compatibleCapabilities),
                entry("missing-image-input", missingImageInput),
                entry("missing-text-output", missingTextOutput),
            ),
        )

        result.map { it.id } shouldBe listOf("compatible")
    }

    @Test
    fun `keeps paid and unknown price models for the picker`() {
        val paid = entry("paid", compatibleCapabilities).copy(cost = TranslationModelCost.PAID)
        val unknown = entry("unknown", compatibleCapabilities).copy(cost = TranslationModelCost.UNKNOWN)

        TranslationModelCatalogFilter.filter(listOf(paid, unknown)).map { it.id } shouldBe
            listOf("paid", "unknown")
    }

    @Test
    fun `native filter keeps image capable paid models and drops text only`() {
        val vision = entry("claude-sonnet-4-5", compatibleCapabilities).copy(cost = TranslationModelCost.PAID)
        val textOnly = entry("text-only", compatibleCapabilities.copy(imageInput = false))

        val result = TranslationModelCatalogFilter.filter(listOf(vision, textOnly), TranslationProvider.CLAUDE)

        result.map { it.id } shouldBe listOf("claude-sonnet-4-5")
    }

    @Test
    fun `native filter drops models without text output`() {
        val noText = entry("no-text", compatibleCapabilities.copy(textOutput = false))

        TranslationModelCatalogFilter.filter(listOf(noText), TranslationProvider.GOOGLE) shouldBe emptyList()
    }

    @Test
    fun `automatic OpenRouter filter requires output capacity`() {
        val strictCompatible = entry("free-vision", compatibleCapabilities)
        val paidVision = entry("paid-vision", compatibleCapabilities)
            .copy(cost = TranslationModelCost.PAID)
        val noLimit = entry("no-limit", compatibleCapabilities)
            .copy(capabilities = compatibleCapabilities.copy(maxOutputTokens = null))
        val lowLimit = entry("low-limit", compatibleCapabilities)
            .copy(capabilities = compatibleCapabilities.copy(maxOutputTokens = 1_024))

        val result = TranslationModelCatalogFilter.filter(
            listOf(strictCompatible, paidVision, noLimit, lowLimit),
            TranslationProvider.OPENROUTER,
        )

        result.map { it.id } shouldBe listOf("free-vision", "paid-vision", "no-limit", "low-limit")
        TranslationModelCatalogFilter.filterForAutomatic(
            listOf(strictCompatible, paidVision, noLimit, lowLimit),
            TranslationProvider.OPENROUTER,
        ).map { it.id } shouldBe listOf("free-vision", "paid-vision")
    }

    @Test
    fun `single argument filter delegates to OpenRouter picker visibility`() {
        val paidVision = entry("paid-vision", compatibleCapabilities).copy(cost = TranslationModelCost.PAID)

        TranslationModelCatalogFilter.filter(listOf(paidVision)).map { it.id } shouldBe listOf("paid-vision")
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
