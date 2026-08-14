package tachiyomi.domain.updates.manga

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.Test
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates
import tachiyomi.domain.updates.manga.repository.MangaUpdatesRepository
import java.time.Instant

class GetMangaUpdatesTest {

    private val repository = mockk<MangaUpdatesRepository>()
    private val interactor = GetMangaUpdates(repository)

    @Test
    fun `subscribe with category filters delegates with epoch millis and limit 500`() {
        val instant = Instant.ofEpochMilli(1_234_567L)
        val included = listOf(1L, 2L)
        val excluded = listOf(3L)
        every {
            repository.subscribeAllMangaUpdatesWithCategoryFilter(
                after = 1_234_567L,
                limit = 500L,
                includedCategories = included,
                excludedCategories = excluded,
            )
        } returns emptyFlow()

        interactor.subscribe(instant, included, excluded)

        verify(exactly = 1) {
            repository.subscribeAllMangaUpdatesWithCategoryFilter(
                after = 1_234_567L,
                limit = 500L,
                includedCategories = included,
                excludedCategories = excluded,
            )
        }
    }
}
