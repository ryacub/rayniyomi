package eu.kanade.tachiyomi.data.translation.catalog

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class TranslationModelPricingFormatterTest {

    @Test
    fun `formats bounded pricing fields with units`() {
        val model = TranslationModelEntry(
            id = "openai/gpt-4o",
            displayName = "GPT-4o",
            capabilities = capabilities,
            cost = TranslationModelCost.PAID,
            freeTierEligible = null,
            stability = TranslationModelStability.UNKNOWN,
            dataTerms = null,
            pricing = mapOf(
                "prompt" to "0.0000025",
                "completion" to "0.00001",
                "image" to "0.000003",
                "request" to "0.01",
                "unexpected" to "ignored",
            ),
        )

        val summary = TranslationModelPricingFormatter.format(model)

        summary shouldContain "prompt 0.0000025 USD/token"
        summary shouldContain "completion 0.00001 USD/token"
        summary shouldContain "image 0.000003 USD/image"
        summary shouldContain "request 0.01 USD/request"
        summary.contains("unexpected") shouldBe false
    }

    @Test
    fun `labels missing price fields as unknown`() {
        val model = TranslationModelEntry(
            id = "unknown",
            displayName = "Unknown",
            capabilities = capabilities,
            cost = TranslationModelCost.UNKNOWN,
            freeTierEligible = null,
            stability = TranslationModelStability.UNKNOWN,
            dataTerms = null,
        )

        TranslationModelPricingFormatter.format(model) shouldBe "pricing unknown"
    }

    private companion object {
        val capabilities = TranslationModelCapabilities(
            imageInput = true,
            textOutput = true,
            multilingualOcrAndTranslation = false,
            spatialBounds = false,
            normalizedCoordinates = false,
            originalAndTranslatedFields = false,
            maxOutputTokens = 4_096,
            structuredJsonOutput = false,
        )
    }
}
