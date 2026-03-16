package eu.kanade.tachiyomi.ui.discover

import eu.kanade.domain.track.enrichment.DiscoverFeedCoordinator
import eu.kanade.domain.track.enrichment.model.DiscoverFeedItem
import eu.kanade.domain.track.enrichment.model.DiscoverReason
import eu.kanade.domain.track.enrichment.model.DiscoverReasonKind
import eu.kanade.domain.track.enrichment.model.EnrichmentMediaType
import eu.kanade.domain.track.enrichment.model.RecommendationChoice
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverScreenModelTest {

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
    fun `initial state has loading=true`() = runTest {
        val coordinator = mockk<DiscoverFeedCoordinator>()
        every { coordinator.observe(limit = 40) } returns flowOf()
        coEvery { coordinator.refresh(limit = 40, force = false) } returns emptyList()

        val model = DiscoverScreenModel(coordinator)

        // Initial state should have loading=true
        assertTrue(model.state.value.loading)
        assertEquals(emptyList<DiscoverFeedItem>(), model.state.value.items)
        assertEquals(null, model.state.value.errorText)
    }

    @Test
    fun `successful flow collection updates state with items and loading=false`() = runTest {
        val coordinator = mockk<DiscoverFeedCoordinator>()
        val testItems = listOf(
            createTestDiscoverFeedItem(stableKey = "key-1", title = "Test Item 1"),
            createTestDiscoverFeedItem(stableKey = "key-2", title = "Test Item 2"),
        )

        every { coordinator.observe(limit = 40) } returns flowOf(testItems)
        coEvery { coordinator.refresh(limit = 40, force = false) } returns emptyList()

        val model = DiscoverScreenModel(coordinator)

        // Eventually, state should be updated with items and loading=false
        assertEquals(testItems, model.state.value.items)
        assertFalse(model.state.value.loading)
        assertEquals(null, model.state.value.errorText)
    }

    @Test
    fun `error in flow triggers catch block with loading=false and errorText populated`() = runTest {
        val coordinator = mockk<DiscoverFeedCoordinator>()
        val testError = RuntimeException("Network error occurred")

        every { coordinator.observe(limit = 40) } returns throwingFlow(testError)
        coEvery { coordinator.refresh(limit = 40, force = false) } returns emptyList()

        val model = DiscoverScreenModel(coordinator)

        // When error occurs, loading should be false and errorText should be set
        assertFalse(model.state.value.loading)
        assertEquals("Network error occurred", model.state.value.errorText)
        assertEquals(emptyList<DiscoverFeedItem>(), model.state.value.items)
    }

    @Test
    fun `error with null message is handled gracefully`() = runTest {
        val coordinator = mockk<DiscoverFeedCoordinator>()
        val testError = RuntimeException(null as String?) // Null message

        every { coordinator.observe(limit = 40) } returns throwingFlow(testError)
        coEvery { coordinator.refresh(limit = 40, force = false) } returns emptyList()

        val model = DiscoverScreenModel(coordinator)

        // When error has null message, should use fallback message
        assertFalse(model.state.value.loading)
        assertEquals("Unknown error", model.state.value.errorText)
    }

    @Test
    fun `error with blank message is handled gracefully`() = runTest {
        val coordinator = mockk<DiscoverFeedCoordinator>()
        val testError = RuntimeException("   ") // Blank message

        every { coordinator.observe(limit = 40) } returns throwingFlow(testError)
        coEvery { coordinator.refresh(limit = 40, force = false) } returns emptyList()

        val model = DiscoverScreenModel(coordinator)

        // When error has blank message, should use fallback message
        assertFalse(model.state.value.loading)
        assertEquals("Unknown error", model.state.value.errorText)
    }

    @Test
    fun `flow completion clears loading flag`() = runTest {
        val coordinator = mockk<DiscoverFeedCoordinator>()
        val testItems = listOf(
            createTestDiscoverFeedItem(stableKey = "key-1", title = "Item"),
        )

        every { coordinator.observe(limit = 40) } returns flowOf(testItems)
        coEvery { coordinator.refresh(limit = 40, force = false) } returns emptyList()

        val model = DiscoverScreenModel(coordinator)

        // After flow completion, loading should be false
        assertFalse(model.state.value.loading)
    }

    @Test
    fun `error clears previous items and errorText is set`() = runTest {
        val coordinator = mockk<DiscoverFeedCoordinator>()
        val testError = RuntimeException("Connection failed")

        // Flow that throws
        every { coordinator.observe(limit = 40) } returns throwingFlow(testError)
        coEvery { coordinator.refresh(limit = 40, force = false) } returns emptyList()

        val model = DiscoverScreenModel(coordinator)

        // Error state should be properly set
        assertFalse(model.state.value.loading)
        assertEquals("Connection failed", model.state.value.errorText)
    }

    @Test
    fun `cancellation exception in observe is not surfaced as user error`() = runTest {
        val coordinator = mockk<DiscoverFeedCoordinator>()

        every { coordinator.observe(limit = 40) } returns throwingFlow(CancellationException("cancelled"))
        coEvery { coordinator.refresh(limit = 40, force = false) } returns emptyList()

        val model = DiscoverScreenModel(coordinator)

        assertEquals(null, model.state.value.errorText)
    }

    private fun createTestDiscoverFeedItem(
        stableKey: String,
        title: String,
    ): DiscoverFeedItem {
        return DiscoverFeedItem(
            stableKey = stableKey,
            title = title,
            mediaType = EnrichmentMediaType.MANGA,
            targetUrl = "https://example.com/title",
            trackerSources = listOf("AniList"),
            sourceCount = 1,
            confidence = 0.85,
            score = 8.5,
            reason = DiscoverReason(
                kind = DiscoverReasonKind.MULTI_TRACKER,
                label = "Recommended by multiple trackers",
            ),
            alternatives = emptyList(),
        )
    }
}

// Helper function to create a flow that throws an error
private fun <T> throwingFlow(error: Throwable) = flow<T> {
    throw error
}
