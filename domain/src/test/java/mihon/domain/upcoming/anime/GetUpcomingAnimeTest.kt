package mihon.domain.upcoming.anime

import eu.kanade.tachiyomi.animesource.model.SAnime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import mihon.domain.upcoming.anime.interactor.GetUpcomingAnime
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.repository.AnimeRepository

class GetUpcomingAnimeTest {

    private val repository = mockk<AnimeRepository>()
    private val interactor = GetUpcomingAnime(repository)

    @Test
    fun `subscribe with category filters delegates with the status set and both category lists`() = runTest {
        val included = listOf(1L, 2L)
        val excluded = listOf(3L)
        val statuses = setOf(SAnime.ONGOING.toLong(), SAnime.PUBLISHING_FINISHED.toLong())
        coEvery {
            repository.getUpcomingAnime(statuses, included, excluded)
        } returns emptyFlow()

        interactor.subscribe(included, excluded)

        coVerify(exactly = 1) {
            repository.getUpcomingAnime(statuses, included, excluded)
        }
    }

    @Test
    fun `subscribe with empty category lists still delegates`() = runTest {
        val statuses = setOf(SAnime.ONGOING.toLong(), SAnime.PUBLISHING_FINISHED.toLong())
        coEvery {
            repository.getUpcomingAnime(statuses, emptyList(), emptyList())
        } returns emptyFlow()

        interactor.subscribe(emptyList(), emptyList())

        coVerify(exactly = 1) {
            repository.getUpcomingAnime(statuses, emptyList(), emptyList())
        }
    }
}
