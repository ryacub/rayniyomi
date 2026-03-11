package eu.kanade.domain.track.enrichment

import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler

/**
 * Tests for EnrichmentCacheRepositoryImpl JSON serialization helpers.
 *
 * These tests verify that List<String> tracker sources are correctly serialized to JSON
 * and deserialized back, preserving round-trip integrity even when tracker names contain
 * the delimiter ", " (comma-space).
 */
class EnrichmentCacheRepositoryImplTest {

    private val mangaHandler = mockk<MangaDatabaseHandler>()
    private val animeHandler = mockk<AnimeDatabaseHandler>()
    private val json = Json
    private val repository = EnrichmentCacheRepositoryImpl(mangaHandler, animeHandler, json)

    @Test
    fun `encodeTrackerSources encodes empty list as JSON array`() {
        val sources = emptyList<String>()
        val encoded = repository.encodeTrackerSources(sources)
        assertEquals("[]", encoded)
    }

    @Test
    fun `decodeTrackerSources decodes empty JSON array back to empty list`() {
        val encoded = "[]"
        val decoded = repository.decodeTrackerSources(encoded)
        assertEquals(emptyList<String>(), decoded)
    }

    @Test
    fun `encodeTrackerSources encodes single item list as JSON array`() {
        val sources = listOf("MyAnimeList")
        val encoded = repository.encodeTrackerSources(sources)
        // Should be valid JSON array string
        assertEquals("""["MyAnimeList"]""", encoded)
    }

    @Test
    fun `decodeTrackerSources decodes single item JSON array back to single item list`() {
        val encoded = """["MyAnimeList"]"""
        val decoded = repository.decodeTrackerSources(encoded)
        assertEquals(listOf("MyAnimeList"), decoded)
    }

    @Test
    fun `encodeTrackerSources encodes multiple items list as JSON array`() {
        val sources = listOf("MyAnimeList", "AniList", "Kitsu")
        val encoded = repository.encodeTrackerSources(sources)
        // Should be valid JSON array string with all three items
        val decoded = Json.decodeFromString<List<String>>(encoded)
        assertEquals(sources, decoded)
    }

    @Test
    fun `decodeTrackerSources decodes multiple items JSON array back to original list`() {
        val sources = listOf("MyAnimeList", "AniList", "Kitsu")
        val encoded = """["MyAnimeList","AniList","Kitsu"]"""
        val decoded = repository.decodeTrackerSources(encoded)
        assertEquals(sources, decoded)
    }

    @Test
    fun `encodeTrackerSources preserves tracker name with comma-space inside it`() {
        val sources = listOf("Tracker, Inc.")
        val encoded = repository.encodeTrackerSources(sources)
        val decoded = Json.decodeFromString<List<String>>(encoded)
        assertEquals(sources, decoded)
    }

    @Test
    fun `decodeTrackerSources preserves tracker name with comma-space inside it`() {
        val originalSources = listOf("Tracker, Inc.")
        val encoded = """["Tracker, Inc."]"""
        val decoded = repository.decodeTrackerSources(encoded)
        assertEquals(originalSources, decoded)
    }

    @Test
    fun `round-trip preserves multiple trackers with special characters`() {
        val sources = listOf("Tracker, Inc.", "MyAnimeList, Ltd.", "Regular Tracker")
        val encoded = repository.encodeTrackerSources(sources)
        val decoded = repository.decodeTrackerSources(encoded)
        assertEquals(sources, decoded)
    }

    @Test
    fun `round-trip single tracker with complex name containing comma-space-comma`() {
        val sources = listOf("Tracker, Inc., MAL")
        val encoded = repository.encodeTrackerSources(sources)
        val decoded = repository.decodeTrackerSources(encoded)
        assertEquals(sources, decoded)
    }

    @Test
    fun `encodeTrackerSources produces valid JSON that can be parsed`() {
        val sources = listOf("Tracker1", "Tracker2")
        val encoded = repository.encodeTrackerSources(sources)
        // Should not throw exception
        val parsed = Json.decodeFromString<List<String>>(encoded)
        assertEquals(sources, parsed)
    }

    @Test
    fun `decodeTrackerSources handles tracker names with quotes`() {
        val sources = listOf("""Tracker "Special"""", "Regular")
        val encoded = repository.encodeTrackerSources(sources)
        val decoded = repository.decodeTrackerSources(encoded)
        assertEquals(sources, decoded)
    }

    @Test
    fun `decodeTrackerSources filters blank items from decoded list`() {
        // The plan specifies filter { it.isNotBlank() } should be applied
        val encoded = """["Tracker1","","Tracker2"]"""
        val decoded = repository.decodeTrackerSources(encoded)
        // Blank items should be filtered out
        assertEquals(listOf("Tracker1", "Tracker2"), decoded)
    }
}
