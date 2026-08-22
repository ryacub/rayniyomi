package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OpenRouterCatalogParserTest {

    @Test
    fun `parses compatible free OpenRouter model`() {
        val body = """
            {"data":[{
              "id":"google/gemma-4-26b-a4b-it:free",
              "name":"Example Free Vision",
              "context_length":16384,
              "architecture":{"input_modalities":["text","image"],"output_modalities":["text"]},
              "top_provider":{"max_completion_tokens":8192},
              "supported_parameters":["response_format"],
              "pricing":{"prompt":"0","completion":"0"}
            }]}
        """.trimIndent()

        val catalog = OpenRouterCatalogParser.parse(body, 1_000)

        catalog.provider shouldBe TranslationProvider.OPENROUTER
        catalog.fetchedAtEpochMilliseconds shouldBe 1_000
        val model = catalog.models.single()
        model.id shouldBe "google/gemma-4-26b-a4b-it:free"
        model.capabilities.imageInput shouldBe true
        model.capabilities.textOutput shouldBe true
        model.capabilities.multilingualOcrAndTranslation shouldBe true
        model.capabilities.spatialBounds shouldBe true
        model.capabilities.normalizedCoordinates shouldBe true
        model.capabilities.originalAndTranslatedFields shouldBe true
        model.capabilities.minimumOutputTokens shouldBe 8_192
        model.cost shouldBe TranslationModelCost.FREE
    }

    @Test
    fun `skips malformed model but parses valid model`() {
        val body = """
            {"data":[{"id":""},{"id":"example/text-only","context_length":4096}]}
        """.trimIndent()

        val catalog = OpenRouterCatalogParser.parse(body, 1_000)

        TranslationModelCatalogFilter.filter(catalog.models) shouldBe emptyList()
        catalog.models.single().id shouldBe "example/text-only"
    }

    @Test
    fun `rejects response without model list`() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenRouterCatalogParser.parse("""{"error":"catalog unavailable"}""", 1_000)
        }
    }
}
