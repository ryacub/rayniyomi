package eu.kanade.tachiyomi.ui.browse.manga.migration.manga

import eu.kanade.tachiyomi.source.MangaSource
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
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.source.manga.service.MangaSourceManager

@OptIn(ExperimentalCoroutinesApi::class)
class MigrateMangaScreenModelTest {

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
        val sourceManager = mockk<MangaSourceManager>()
        val getFavorites = mockk<GetMangaFavorites>()

        every { sourceManager.getOrStub(1L) } returns mockk<MangaSource>(relaxed = true)
        every { getFavorites.subscribe(1L) } returns flow { throw RuntimeException("boom") }

        val model = MigrateMangaScreenModel(
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
        assertEquals(MigrationMangaEvent.FailedFetchingFavorites, event)
    }
}
