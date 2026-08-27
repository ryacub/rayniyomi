package eu.kanade.tachiyomi.data.translation.engine

import eu.kanade.tachiyomi.data.translation.InvalidTranslationResponseException
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RegionParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses valid JSON array of regions`() {
        val result = RegionParser.parse(
            """[{"left":0.1,"top":0.05,"right":0.4,"bottom":0.15,"original":"hello","translated":"bonjour"}]""",
            json,
        )

        assertEquals(1, result.regions.size)
        assertEquals("hello", result.regions.single().originalText)
        assertEquals("bonjour", result.regions.single().translatedText)
    }

    @Test
    fun `valid empty array is a no-text result`() {
        assertEquals(emptyList<Any>(), RegionParser.parse("[]", json).regions)
    }

    @Test
    fun `missing array is invalid provider output`() {
        assertThrows<InvalidTranslationResponseException> {
            RegionParser.parse("No JSON response", json)
        }
    }

    @Test
    fun `malformed array is invalid provider output`() {
        assertThrows<InvalidTranslationResponseException> {
            RegionParser.parse("[{\"left\": 0.1}", json)
        }
    }

    @Test
    fun `unusable region is invalid provider output`() {
        assertThrows<InvalidTranslationResponseException> {
            RegionParser.parse(
                "[{\"left\": 0.1,\"top\": 0.1,\"right\": 0.1,\"bottom\": 0.2,\"original\":\"a\",\"translated\":\"b\"}]",
                json,
            )
        }
    }

    @Test
    fun `region outside the image is invalid provider output`() {
        assertThrows<InvalidTranslationResponseException> {
            RegionParser.parse(
                "[{\"left\":1.1,\"top\":0.1,\"right\":1.2,\"bottom\":0.2,\"original\":\"a\",\"translated\":\"b\"}]",
                json,
            )
        }
    }

    @Test
    fun `extracts an array from surrounding response text`() {
        val result = RegionParser.parse(
            "Here are the regions: [{\"left\":0.1,\"top\":0.1,\"right\":0.4,\"bottom\":0.4,\"original\":\"a\",\"translated\":\"b\"}]",
            json,
        )

        assertEquals(1, result.regions.size)
    }

    @Test
    fun `clamps usable coordinates to the image bounds`() {
        val result = RegionParser.parse(
            "[{\"left\":-0.1,\"top\":0.1,\"right\":0.5,\"bottom\":1.5,\"original\":\"a\",\"translated\":\"b\"}]",
            json,
        )

        assertEquals(0f, result.regions.single().bounds.left)
        assertEquals(1f, result.regions.single().bounds.bottom)
    }

    @Test
    fun `missing region fields are invalid provider output`() {
        assertThrows<InvalidTranslationResponseException> {
            RegionParser.parse("[{\"left\":0.1,\"top\":0.2,\"right\":0.3,\"bottom\":0.4}]", json)
        }
    }
}
