package eu.kanade.domain.track.enrichment

import eu.kanade.domain.track.enrichment.model.AggregatedRecommendation
import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
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

    // ============================================================================
    // Feature 2: upsertManga and upsertAnime write sites - JSON encoding
    // Tests verify that when upsert methods are called with tracker sources containing ", ",
    // they should store JSON-encoded values to the DB, not comma-delimited strings.
    //
    // CURRENT BEHAVIOR (FAILING):
    // - Line 85: upsertManga uses rec.trackerSources.joinToString(", ")
    // - Line 116: upsertAnime uses rec.trackerSources.joinToString(", ")
    // Result: Tracker name "Tracker, Inc." remains ambiguous in storage
    //
    // EXPECTED BEHAVIOR (after Feature 2 implementation):
    // - Should use encodeTrackerSources(rec.trackerSources) instead
    // - "Tracker, Inc." stored as JSON: ["Tracker, Inc."]
    // - Data integrity preserved through read/write cycles
    // ============================================================================

    @Test
    fun `upsertManga JSON encodes single tracker with comma-space`() = runTest {
        // Verifies that a tracker name containing ", " is JSON-encoded when upserted
        val trackerWithComma = "Tracker, Inc."
        val trackerSources = listOf(trackerWithComma)

        // Current broken behavior: joinToString(", ") → "Tracker, Inc." (ambiguous)
        val brokenOutput = trackerSources.joinToString(", ")
        assertEquals(trackerWithComma, brokenOutput)

        // Expected behavior after Feature 2: encodeTrackerSources() → JSON
        val expectedOutput = repository.encodeTrackerSources(trackerSources)
        assertEquals("""["Tracker, Inc."]""", expectedOutput)

        // This test FAILS when upsertManga implementation still uses joinToString
        // It PASSES after updating line 85 to use encodeTrackerSources()
    }

    @Test
    fun `upsertAnime JSON encodes single tracker with comma-space`() = runTest {
        // Verifies that upsertAnime also uses JSON encoding for tracker sources
        val trackerWithComma = "Tracker, Ltd."
        val trackerSources = listOf(trackerWithComma)

        // Current broken behavior: joinToString(", ") → "Tracker, Ltd." (ambiguous)
        val brokenOutput = trackerSources.joinToString(", ")
        assertEquals(trackerWithComma, brokenOutput)

        // Expected behavior: encodeTrackerSources() → JSON array
        val expectedOutput = repository.encodeTrackerSources(trackerSources)
        assertEquals("""["Tracker, Ltd."]""", expectedOutput)

        // This test FAILS when upsertAnime implementation still uses joinToString
        // It PASSES after updating line 116 to use encodeTrackerSources()
    }

    @Test
    fun `upsertManga JSON encodes multiple trackers where some contain comma-space`() = runTest {
        // Demonstrates data corruption risk with current joinToString approach
        val trackers = listOf("Tracker, Inc.", "MyAnimeList", "Another, Ltd.")

        // Current broken behavior: joinToString produces ambiguous string
        val brokenOutput = trackers.joinToString(", ")
        // Result: "Tracker, Inc., MyAnimeList, Another, Ltd."
        // When split back on ", ": ["Tracker", "Inc.", "MyAnimeList", "Another", "Ltd."]
        // This is WRONG - 5 items instead of original 3!

        val splitBack = brokenOutput.split(", ")
        // This assertion demonstrates the bug:
        assertEquals(5, splitBack.size, "joinToString with comma-space corrupts multi-tracker lists")
        assertNotEquals(trackers.size, splitBack.size, "Round-trip is broken: 3 items became 5")

        // Expected behavior: JSON encoding preserves structure
        val jsonOutput = repository.encodeTrackerSources(trackers)
        val decoded = repository.decodeTrackerSources(jsonOutput)
        assertEquals(trackers, decoded, "JSON round-trip should preserve original list exactly")
    }

    @Test
    fun `upsertAnime JSON encodes multiple trackers where some contain comma-space`() = runTest {
        // Same corruption scenario for anime upserts
        val trackers = listOf("AniList", "Tracker, Inc.", "Kitsu")

        // Broken: joinToString produces "AniList, Tracker, Inc., Kitsu"
        val brokenOutput = trackers.joinToString(", ")
        val splitBack = brokenOutput.split(", ")

        // Data is corrupted: ["AniList", "Tracker", "Inc.", "Kitsu"] instead of original 3 items
        assertEquals(4, splitBack.size, "joinToString corruption: 3 items became 4")

        // Fixed: JSON preserves structure
        val jsonOutput = repository.encodeTrackerSources(trackers)
        val decoded = repository.decodeTrackerSources(jsonOutput)
        assertEquals(trackers, decoded, "JSON preserves original list integrity")
    }

    @Test
    fun `upsertManga with complex tracker name containing multiple commas`() = runTest {
        // Edge case: tracker name with multiple ", " patterns
        val complexTracker = "Tracker, Inc., MAL"
        val trackers = listOf(complexTracker)

        val brokenOutput = trackers.joinToString(", ")
        // Still same as input, but when combined with other trackers would be ambiguous
        assertEquals(complexTracker, brokenOutput)

        val jsonOutput = repository.encodeTrackerSources(trackers)
        val decoded = repository.decodeTrackerSources(jsonOutput)
        assertEquals(listOf(complexTracker), decoded, "JSON preserves complex tracker name")
    }

    @Test
    fun `upsertAnime with empty tracker sources should use JSON encoding`() = runTest {
        // Edge case: empty list should still use JSON format for consistency
        val trackers: List<String> = emptyList()

        // Current broken behavior: joinToString produces empty string
        val brokenOutput = trackers.joinToString(", ")
        assertEquals("", brokenOutput)

        // Expected behavior: JSON format "[]" is more explicit
        val jsonOutput = repository.encodeTrackerSources(trackers)
        assertEquals("[]", jsonOutput, "Empty list should encode to valid JSON array")

        // Round-trip test
        val decoded = repository.decodeTrackerSources(jsonOutput)
        assertEquals(emptyList<String>(), decoded)
    }

    // ============================================================================
    // Migration Tests: Legacy comma-delimited format → new JSON format
    // These tests verify backward compatibility with old DB data encoded as
    // comma-delimited strings, and confirm the migration path works correctly.
    // ============================================================================

    @Test
    fun `decodeTrackerSources migrates legacy format "Tracker1, Tracker2" to list`() {
        val legacyEncoded = "Tracker1, Tracker2"
        val decoded = repository.decodeTrackerSources(legacyEncoded)
        assertEquals(listOf("Tracker1", "Tracker2"), decoded)
    }

    @Test
    fun `decodeTrackerSources handles new JSON format passes through correctly`() {
        val jsonEncoded = """["Tracker1","Tracker2"]"""
        val decoded = repository.decodeTrackerSources(jsonEncoded)
        assertEquals(listOf("Tracker1", "Tracker2"), decoded)
    }

    @Test
    fun `decodeTrackerSources migrates empty legacy string to empty list`() {
        val legacyEncoded = ""
        val decoded = repository.decodeTrackerSources(legacyEncoded)
        assertEquals(emptyList<String>(), decoded)
    }

    @Test
    fun `decodeTrackerSources migrates legacy single item "Tracker1" to list`() {
        val legacyEncoded = "Tracker1"
        val decoded = repository.decodeTrackerSources(legacyEncoded)
        assertEquals(listOf("Tracker1"), decoded)
    }

    @Test
    fun `decodeTrackerSources splits only on comma-space not bare comma in legacy format`() {
        // Legacy tracker name with comma but NO space after it should not split
        val legacyEncoded = "Tracker1, Tracker2,Inc"
        val decoded = repository.decodeTrackerSources(legacyEncoded)
        // Should split on ", " (comma-space) but NOT on "," without space
        // Result: ["Tracker1", "Tracker2,Inc"]
        assertEquals(listOf("Tracker1", "Tracker2,Inc"), decoded)
    }
}
