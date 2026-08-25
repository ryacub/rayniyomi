package eu.kanade.tachiyomi.data.translation.catalog

import eu.kanade.tachiyomi.data.translation.TranslationProvider
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ClaudeCatalogParserTest {

    @Test
    fun `parses Claude model with display name`() {
        val body = """
            {"data":[{
              "id":"claude-sonnet-4-5",
              "type":"model",
              "display_name":"Claude Sonnet 4.5",
              "created_at":"2025-09-29T00:00:00Z"
            }],"has_more":false,"last_id":"claude-sonnet-4-5"}
        """.trimIndent()

        val catalog = ClaudeCatalogParser.parse(body, 1_000)

        catalog.provider shouldBe TranslationProvider.CLAUDE
        catalog.fetchedAtEpochMilliseconds shouldBe 1_000
        val model = catalog.models.single()
        model.id shouldBe "claude-sonnet-4-5"
        model.displayName shouldBe "Claude Sonnet 4.5"
        model.capabilities.imageInput shouldBe true
        model.capabilities.textOutput shouldBe true
        model.capabilities.minimumOutputTokens shouldBe null
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
    fun `uses api image input capability when present`() {
        val body = """
            {"data":[{
              "id":"claude-sonnet-4-5",
              "type":"model",
              "display_name":"Claude Sonnet 4.5",
              "capabilities":{"image_input":{"supported":true}}
            }]}
        """.trimIndent()

        val model = ClaudeCatalogParser.parse(body, 1_000).models.single()

        model.capabilities.imageInput shouldBe true
    }

    @Test
    fun `drops image input when the api reports it unsupported`() {
        val body = """
            {"data":[{
              "id":"claude-3-haiku",
              "type":"model",
              "capabilities":{"image_input":{"supported":false}}
            }]}
        """.trimIndent()

        val model = ClaudeCatalogParser.parse(body, 1_000).models.single()

        model.capabilities.imageInput shouldBe false
    }

    @Test
    fun `falls back to image input true when capability data is missing`() {
        val body = """
            {"data":[
              {"id":"claude-opus-4-1","type":"model"},
              {"id":"claude-sonnet-4-5","type":"model","capabilities":{}},
              {"id":"claude-haiku-4-5","type":"model","capabilities":{"image_input":{}}}
            ]}
        """.trimIndent()

        val models = ClaudeCatalogParser.parse(body, 1_000).models

        models.map { it.capabilities.imageInput } shouldBe listOf(true, true, true)
    }

    @Test
    fun `falls back to id when display name is absent`() {
        val body = """{"data":[{"id":"claude-opus-4-1","type":"model"}]}"""

        val model = ClaudeCatalogParser.parse(body, 1_000).models.single()

        model.id shouldBe "claude-opus-4-1"
        model.displayName shouldBe "claude-opus-4-1"
    }

    @Test
    fun `skips malformed entries but parses valid ones`() {
        val body = """
            {"data":[
              {},
              {"id":"claude-haiku-4-5","type":"model","display_name":"Claude Haiku 4.5"}
            ]}
        """.trimIndent()

        val catalog = ClaudeCatalogParser.parse(body, 1_000)

        catalog.models.single().id shouldBe "claude-haiku-4-5"
    }

    @Test
    fun `rejects response without model list`() {
        assertThrows(IllegalArgumentException::class.java) {
            ClaudeCatalogParser.parse("""{"error":"unavailable"}""", 1_000)
        }
    }

    @Test
    fun `exposes pagination cursor fields`() {
        val body = """
            {"data":[{"id":"claude-sonnet-4-5","type":"model"}],
             "has_more":true,"last_id":"claude-sonnet-4-5"}
        """.trimIndent()

        val page = ClaudeCatalogParser.parsePage(body)
        page.hasMore shouldBe true
        page.lastId shouldBe "claude-sonnet-4-5"
        page.models.single().id shouldBe "claude-sonnet-4-5"
    }
}
