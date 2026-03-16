package eu.kanade.tachiyomi.ui.browse.anime.migration.anime

import eu.kanade.tachiyomi.animesource.AnimeSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@OptIn(ExperimentalCoroutinesApi::class)
class MigrateAnimeScreenModelTest {

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
    fun `favorite loading failure is buffered and delivered after delayed collector attach`() = runTest {
        val sourceManager = mockk<AnimeSourceManager>()
        val getFavorites = mockk<GetAnimeFavorites>()

        every { sourceManager.getOrStub(1L) } returns mockk<AnimeSource>(relaxed = true)
        every { getFavorites.subscribe(1L) } returns flow { throw RuntimeException("boom") }

        val model = MigrateAnimeScreenModel(
            sourceId = 1L,
            sourceManager = sourceManager,
            getFavorites = getFavorites,
        )

        advanceUntilIdle()

        // If event emission suspended, loading never clears because titleList update is after send().
        assertFalse(model.state.value.isLoading)

        val event = withTimeout(1_000) {
            model.events.first()
        }
        assertEquals(MigrationAnimeEvent.FailedFetchingFavorites, event)
    }
}
