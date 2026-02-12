package eu.kanade.tachiyomi.data.translation.engine

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegionParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses valid JSON array of regions`() {
        val text = """[
            {"left":0.1,"top":0.05,"right":0.4,"bottom":0.15,"original":"こんにちは","translated":"Hello"},
            {"left":0.5,"top":0.2,"right":0.9,"bottom":0.35,"original":"世界","translated":"World"}
        ]"""

        val result = RegionParser.parse(text, json)
        assertEquals(2, result.regions.size)

        val first = result.regions[0]
        assertEquals(0.1f, first.bounds.left, 0.001f)
        assertEquals(0.05f, first.bounds.top, 0.001f)
        assertEquals(0.4f, first.bounds.right, 0.001f)
        assertEquals(0.15f, first.bounds.bottom, 0.001f)
        assertEquals("こんにちは", first.originalText)
        assertEquals("Hello", first.translatedText)

        val second = result.regions[1]
        assertEquals("世界", second.originalText)
        assertEquals("World", second.translatedText)
    }

    @Test
    fun `returns empty regions for empty array`() {
        val result = RegionParser.parse("[]", json)
        assertTrue(result.regions.isEmpty())
    }

    @Test
    fun `returns empty regions for no JSON`() {
        val result = RegionParser.parse("No text found on this page.", json)
        assertTrue(result.regions.isEmpty())
    }

    @Test
    fun `extracts JSON from surrounding text`() {
        val text = """Here are the detected regions:
[{"left":0.1,"top":0.2,"right":0.3,"bottom":0.4,"original":"test","translated":"test"}]
That's all I found."""

        val result = RegionParser.parse(text, json)
        assertEquals(1, result.regions.size)
    }

    @Test
    fun `clamps coordinates to 0-1 range`() {
        val text = """[{"left":-0.1,"top":1.5,"right":0.5,"bottom":0.5,"original":"test","translated":"test"}]"""

        val result = RegionParser.parse(text, json)
        assertEquals(1, result.regions.size)
        assertEquals(0f, result.regions[0].bounds.left, 0.001f)
        assertEquals(1f, result.regions[0].bounds.top, 0.001f)
    }

    @Test
    fun `handles malformed JSON gracefully`() {
        val text = """[{"left":0.1, this is broken}]"""
        val result = RegionParser.parse(text, json)
        assertTrue(result.regions.isEmpty())
    }

    @Test
    fun `handles missing optional fields with defaults`() {
        val text = """[{"left":0.1,"top":0.2,"right":0.3,"bottom":0.4}]"""
        val result = RegionParser.parse(text, json)
        assertEquals(1, result.regions.size)
        assertEquals("", result.regions[0].originalText)
        assertEquals("", result.regions[0].translatedText)
    }
}
