package tachiyomi.domain.updates.anime

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Test
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.anime.repository.AnimeUpdatesRepository
import java.time.Instant

class GetAnimeUpdatesTest {

    private val repository = mockk<AnimeUpdatesRepository>()
    private val interactor = GetAnimeUpdates(repository)

    @Test
    fun `subscribe with category filters delegates with epoch millis and limit 500`() {
        val instant = Instant.ofEpochMilli(1_234_567L)
        val included = listOf(1L, 2L)
        val excluded = listOf(3L)
        every {
            repository.subscribeAllAnimeUpdatesWithCategoryFilter(
                after = 1_234_567L,
                limit = 500L,
                includedCategories = included,
                excludedCategories = excluded,
            )
        } returns emptyFlow()

        interactor.subscribe(instant, included, excluded)

        verify(exactly = 1) {
            repository.subscribeAllAnimeUpdatesWithCategoryFilter(
                after = 1_234_567L,
                limit = 500L,
                includedCategories = included,
                excludedCategories = excluded,
            )
        }
    }

    @Test
    fun `old subscribe overload still delegates unchanged`() {
        val instant = Instant.ofEpochMilli(2_345_678L)
        every { repository.subscribeAllAnimeUpdates(after = 2_345_678L, limit = 500L) } returns emptyFlow()

        interactor.subscribe(instant)

        verify(exactly = 1) {
            repository.subscribeAllAnimeUpdates(after = 2_345_678L, limit = 500L)
        }
    }
}
