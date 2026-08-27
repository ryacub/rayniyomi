package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class OpenAICatalogParserTest {

    @Test
    fun `parses vision capable OpenAI model`() {
        val body = """
            {"object":"list","data":[
              {"id":"gpt-4o","created":1715367049,"owned_by":"system"}
            ]}
        """.trimIndent()

        val catalog = OpenAICatalogParser.parse(body, 2_000)

        catalog.provider shouldBe TranslationProvider.OPENAI
        catalog.fetchedAtEpochMilliseconds shouldBe 2_000
        val model = catalog.models.single()
        model.id shouldBe "gpt-4o"
        model.displayName shouldBe "gpt-4o"
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
    fun `keeps chat families and drops everything else`() {
        val body = """
            {"object":"list","data":[
              {"id":"gpt-4o","created":1715367049,"owned_by":"system"},
              {"id":"o3","created":1737000000,"owned_by":"system"},
              {"id":"chatgpt-4o-latest","created":1718000000,"owned_by":"system"},
              {"id":"dall-e-3","created":1695120000,"owned_by":"system"},
              {"id":"whisper-1","created":1677610000,"owned_by":"openai"},
              {"id":"text-embedding-3-small","created":1709000000,"owned_by":"openai"}
            ]}
        """.trimIndent()

        val catalog = OpenAICatalogParser.parse(body, 1_000)

        catalog.models.map { it.id } shouldBe listOf("gpt-4o", "o3", "chatgpt-4o-latest")
    }

    @Test
    fun `drops documented text-only models`() {
        val body = """
            {"object":"list","data":[
              {"id":"gpt-3.5-turbo","created":1677610000,"owned_by":"openai"},
              {"id":"gpt-3.5-turbo-16k","created":1683000000,"owned_by":"openai"},
              {"id":"o1-mini","created":1724000000,"owned_by":"system"},
              {"id":"o3-mini","created":1735000000,"owned_by":"system"},
              {"id":"gpt-4o","created":1715367049,"owned_by":"system"}
            ]}
        """.trimIndent()

        val catalog = OpenAICatalogParser.parse(body, 1_000)

        catalog.models.map { it.id } shouldBe listOf("gpt-4o")
    }

    @Test
    fun `rejects response without model list`() {
        assertThrows(IllegalArgumentException::class.java) {
            OpenAICatalogParser.parse("""{"error":{"message":"incorrect api key"}}""", 1_000)
        }
    }
}
