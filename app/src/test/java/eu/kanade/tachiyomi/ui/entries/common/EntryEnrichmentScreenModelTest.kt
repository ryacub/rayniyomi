package eu.kanade.tachiyomi.ui.entries.common

import eu.kanade.domain.track.enrichment.EntryEnrichmentCoordinator
import eu.kanade.domain.track.enrichment.model.AggregatedRecommendation
import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EntryEnrichmentScreenModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has loading=true before any observer emits`() {
        val defaultState = EntryEnrichmentScreenModel.State()
        assertEquals(true, defaultState.loading)
        assertNull(defaultState.entry)
        assertNull(defaultState.errorText)
    }

    @Test
    fun `observer survives refresh failure - observer continues emitting after refresh throws`() = runTest {
        val coordinator = mockk<EntryEnrichmentCoordinator>()
        val testEntry = createTestEnrichedEntry(entryId = 1)

        // Observer flow emits a valid entry
        every { coordinator.observeManga(1) } returns flowOf(testEntry)

        // Refresh operation throws an exception
        coEvery { coordinator.refreshManga(mangaId = 1, title = "Test Manga", force = true) } throws
            RuntimeException("Refresh failed due to network error")

        val model = EntryEnrichmentScreenModel(
            entryId = 1,
            title = "Test Manga",
            mediaType = EnrichmentMediaType.MANGA,
            coordinator = coordinator,
        )

        advanceUntilIdle()

        // CRITICAL: Observer should have emitted the entry from cache flow despite refresh exception
        // Currently FAILS without supervisorScope because the refresh exception cancels the observer
        assertEquals(testEntry, model.state.value.entry)
        assertFalse(model.state.value.loading)
        assertEquals("Refresh failed due to network error", model.state.value.errorText)
    }

    @Test
    fun `observer emits entry and loading becomes false`() = runTest {
        val coordinator = mockk<EntryEnrichmentCoordinator>()
        val testEntry = createTestEnrichedEntry(entryId = 1)

        every { coordinator.observeManga(1) } returns flowOf(testEntry)
        coEvery { coordinator.refreshManga(mangaId = 1, title = "Test Manga", force = true) } returns testEntry

        val model = EntryEnrichmentScreenModel(
            entryId = 1,
            title = "Test Manga",
            mediaType = EnrichmentMediaType.MANGA,
            coordinator = coordinator,
        )

        advanceUntilIdle()

        assertEquals(testEntry, model.state.value.entry)
        assertFalse(model.state.value.loading)
        assertNull(model.state.value.errorText)
    }

    @Test
    fun `observer with anime media type emits entry`() = runTest {
        val coordinator = mockk<EntryEnrichmentCoordinator>()
        val testEntry = createTestEnrichedEntry(entryId = 2, mediaType = EnrichmentMediaType.ANIME)

        every { coordinator.observeAnime(2) } returns flowOf(testEntry)
        coEvery { coordinator.refreshAnime(animeId = 2, title = "Test Anime", force = true) } returns testEntry

        val model = EntryEnrichmentScreenModel(
            entryId = 2,
            title = "Test Anime",
            mediaType = EnrichmentMediaType.ANIME,
            coordinator = coordinator,
        )

        advanceUntilIdle()

        assertEquals(testEntry, model.state.value.entry)
        assertFalse(model.state.value.loading)
    }

    @Test
    fun `refresh failure sets errorText without clearing entry from observer`() = runTest {
        val coordinator = mockk<EntryEnrichmentCoordinator>()
        val testEntry = createTestEnrichedEntry(entryId = 1)

        // Observer emits the cached entry
        every { coordinator.observeManga(1) } returns flowOf(testEntry)

        // Refresh throws with a specific error message
        val errorMessage = "Unable to sync recommendations"
        coEvery { coordinator.refreshManga(mangaId = 1, title = "Test Manga", force = true) } throws
            RuntimeException(errorMessage)

        val model = EntryEnrichmentScreenModel(
            entryId = 1,
            title = "Test Manga",
            mediaType = EnrichmentMediaType.MANGA,
            coordinator = coordinator,
        )

        advanceUntilIdle()

        // Entry should be preserved from observer
        assertEquals(testEntry, model.state.value.entry)
        // Error should be set from refresh failure
        assertEquals(errorMessage, model.state.value.errorText)
        // loading should be false
        assertFalse(model.state.value.loading)
    }

    @Test
    fun `manga observer failure updates error state and clears loading`() = runTest {
        val coordinator = mockk<EntryEnrichmentCoordinator>()

        every { coordinator.observeManga(1) } returns throwingFlow(RuntimeException("Observer failed"))
        coEvery { coordinator.refreshManga(mangaId = 1, title = "Test Manga", force = true) } returns
            createTestEnrichedEntry(entryId = 1)

        val model = EntryEnrichmentScreenModel(
            entryId = 1,
            title = "Test Manga",
            mediaType = EnrichmentMediaType.MANGA,
            coordinator = coordinator,
        )

        advanceUntilIdle()

        assertFalse(model.state.value.loading)
        assertEquals("Observer failed", model.state.value.errorText)
    }

    @Test
    fun `anime observer failure with blank message uses fallback`() = runTest {
        val coordinator = mockk<EntryEnrichmentCoordinator>()

        every { coordinator.observeAnime(2) } returns throwingFlow(RuntimeException("   "))
        coEvery { coordinator.refreshAnime(animeId = 2, title = "Test Anime", force = true) } returns
            createTestEnrichedEntry(entryId = 2, mediaType = EnrichmentMediaType.ANIME)

        val model = EntryEnrichmentScreenModel(
            entryId = 2,
            title = "Test Anime",
            mediaType = EnrichmentMediaType.ANIME,
            coordinator = coordinator,
        )

        advanceUntilIdle()

        assertFalse(model.state.value.loading)
        assertEquals("Unable to load recommendations", model.state.value.errorText)
    }

    private fun createTestEnrichedEntry(
        entryId: Long,
        mediaType: EnrichmentMediaType = EnrichmentMediaType.MANGA,
    ): EnrichedEntry {
        return EnrichedEntry(
            entryId = entryId,
            mediaType = mediaType,
            mergedTitle = "Test Title",
            compositeScore = 7.5,
            confidenceLabel = "high",
            sourceCoverage = listOf("AniList", "MyAnimeList"),
            summary = "Tracker enrichment updated",
            recommendations = listOf(
                AggregatedRecommendation(
                    stableKey = "rec-1",
                    title = "Recommendation 1",
                    targetUrl = "https://example.com/rec1",
                    trackerSources = listOf("AniList"),
                    sourceCount = 1,
                    confidence = 0.85,
                    inLibrary = false,
                    rankScore = 8.0,
                    alternatives = emptyList(),
                ),
            ),
            failures = emptyList(),
            updatedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + 24 * 60 * 60 * 1000,
        )
    }

    private fun <T> throwingFlow(error: Throwable) = flow<T> {
        throw error
    }
}
