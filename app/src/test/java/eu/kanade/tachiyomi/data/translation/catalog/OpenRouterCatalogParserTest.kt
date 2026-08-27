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
        model.capabilities.multilingualOcrAndTranslation shouldBe false
        model.capabilities.spatialBounds shouldBe false
        model.capabilities.normalizedCoordinates shouldBe false
        model.capabilities.originalAndTranslatedFields shouldBe false
        model.capabilities.maxOutputTokens shouldBe 8_192
        model.cost shouldBe TranslationModelCost.FREE
    }

    @Test
    fun `parses paid model metadata without an allowlist`() {
        val body = """
            {"data":[{
              "id":"openai/gpt-4o",
              "name":"GPT-4o",
              "architecture":{"input_modalities":["text","image"],"output_modalities":["text"]},
              "top_provider":{"max_completion_tokens":8192},
              "supported_parameters":["response_format","tools"],
              "pricing":{"prompt":"0.0000025","completion":"0.00001","image":"0.000003"}
            }]}
        """.trimIndent()

        val model = OpenRouterCatalogParser.parse(body, 1_000).models.single()

        model.id shouldBe "openai/gpt-4o"
        model.capabilities.inputModalities shouldBe listOf("text", "image")
        model.capabilities.outputModalities shouldBe listOf("text")
        model.capabilities.supportedParameters shouldBe listOf("response_format", "tools")
        model.capabilities.maxOutputTokens shouldBe 8_192
        model.pricing shouldBe mapOf(
            "prompt" to "0.0000025",
            "completion" to "0.00001",
            "image" to "0.000003",
        )
        model.cost shouldBe TranslationModelCost.PAID
    }

    @Test
    fun `preserves nested pricing metadata and marks its cost unknown`() {
        val body = """
            {"data":[{
              "id":"example/vision",
              "architecture":{"input_modalities":["image"],"output_modalities":["text"]},
              "pricing":{"prompt":"0","completion":"0","input_cache_read":{"unit":"0.000001"}}
            }]}
        """.trimIndent()

        val model = OpenRouterCatalogParser.parse(body, 1_000).models.single()

        model.pricing["input_cache_read"] shouldBe "{\"unit\":\"0.000001\"}"
        model.cost shouldBe TranslationModelCost.UNKNOWN
    }

    @Test
    fun `marks negative pricing sentinel as unknown`() {
        val body = """
            {"data":[{
              "id":"openrouter/auto",
              "architecture":{"input_modalities":["image"],"output_modalities":["text"]},
              "pricing":{"prompt":"-1","completion":"-1"}
            }]}
        """.trimIndent()

        val model = OpenRouterCatalogParser.parse(body, 1_000).models.single()

        model.cost shouldBe TranslationModelCost.UNKNOWN
        model.freeTierEligible shouldBe null
    }

    @Test
    fun `keeps model when optional metadata has the wrong shape`() {
        val body = """
            {"data":[{
              "id":"example/vision",
              "name":{"unexpected":"shape"},
              "architecture":{"input_modalities":"image","output_modalities":["text"]},
              "supported_parameters":{},
              "pricing":[],
              "top_provider":{"max_completion_tokens":"not-a-number"}
            }]}
        """.trimIndent()

        val model = OpenRouterCatalogParser.parse(body, 1_000).models.single()

        model.id shouldBe "example/vision"
        model.displayName shouldBe "example/vision"
        model.capabilities.imageInput shouldBe false
        model.cost shouldBe TranslationModelCost.UNKNOWN
        model.capabilities.maxOutputTokens shouldBe null
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
