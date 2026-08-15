package mihon.domain.upcoming.manga

import eu.kanade.tachiyomi.source.model.SManga
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import mihon.domain.upcoming.manga.interactor.GetUpcomingManga
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.repository.MangaRepository

class GetUpcomingMangaTest {

    private val repository = mockk<MangaRepository>()
    private val interactor = GetUpcomingManga(repository)

    @Test
    fun `subscribe with category filters delegates with the status set and both category lists`() = runTest {
        val included = listOf(1L, 2L)
        val excluded = listOf(3L)
        val statuses = setOf(SManga.ONGOING.toLong(), SManga.PUBLISHING_FINISHED.toLong())
        coEvery {
            repository.getUpcomingManga(statuses, included, excluded)
        } returns emptyFlow()

        interactor.subscribe(included, excluded)

        coVerify(exactly = 1) {
            repository.getUpcomingManga(statuses, included, excluded)
        }
    }

    @Test
    fun `subscribe with empty category lists still delegates`() = runTest {
        val statuses = setOf(SManga.ONGOING.toLong(), SManga.PUBLISHING_FINISHED.toLong())
        coEvery {
            repository.getUpcomingManga(statuses, emptyList(), emptyList())
        } returns emptyFlow()

        interactor.subscribe(emptyList(), emptyList())

        coVerify(exactly = 1) {
            repository.getUpcomingManga(statuses, emptyList(), emptyList())
        }
    }
}
