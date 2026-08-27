package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GeminiCatalogParserTest {

    @Test
    fun `parses Gemini model with stripped prefix`() {
        val body = """
            {"models":[{
              "name":"models/gemini-2.0-flash",
              "displayName":"Gemini 2.0 Flash",
              "supportedGenerationMethods":["generateContent","countTokens"]
            }]}
        """.trimIndent()

        val catalog = GeminiCatalogParser.parse(body, 3_000)

        catalog.provider shouldBe TranslationProvider.GOOGLE
        catalog.fetchedAtEpochMilliseconds shouldBe 3_000
        val model = catalog.models.single()
        model.id shouldBe "gemini-2.0-flash"
        model.displayName shouldBe "Gemini 2.0 Flash"
        model.capabilities.imageInput shouldBe true
        model.capabilities.textOutput shouldBe true
        model.capabilities.maxOutputTokens shouldBe null
        model.capabilities.multilingualOcrAndTranslation shouldBe false
        model.capabilities.spatialBounds shouldBe false
        model.capabilities.normalizedCoordinates shouldBe false
        model.capabilities.originalAndTranslatedFields shouldBe false
        model.capabilities.structuredJsonOutput shouldBe false
        model.cost shouldBe TranslationModelCost.PAID
        model.freeTierEligible shouldBe null
        model.stability shouldBe TranslationModelStability.UNKNOWN
        model.dataTerms shouldBe null
    }

    @Test
    fun `keeps only generateContent models`() {
        val body = """
            {"models":[
              {"name":"models/text-only","displayName":"Embeddings",
               "supportedGenerationMethods":["embedContent"]},
              {"name":"models/gemini-2.0-flash","displayName":"Gemini 2.0 Flash",
               "supportedGenerationMethods":["generateContent"]}
            ]}
        """.trimIndent()

        val catalog = GeminiCatalogParser.parse(body, 1_000)

        catalog.models.single().id shouldBe "gemini-2.0-flash"
    }

    @Test
    fun `skips malformed entries but parses valid ones`() {
        val body = """
            {"models":[
              {},
              {"name":"models/gemini-2.0-flash-lite","displayName":"Gemini 2.0 Flash Lite",
               "supportedGenerationMethods":["generateContent"]}
            ]}
        """.trimIndent()

        val catalog = GeminiCatalogParser.parse(body, 1_000)

        catalog.models.single().id shouldBe "gemini-2.0-flash-lite"
    }

    @Test
    fun `rejects response without model list`() {
        assertThrows(IllegalArgumentException::class.java) {
            GeminiCatalogParser.parse("""{"error":{"code":400}}""", 1_000)
        }
    }
}
