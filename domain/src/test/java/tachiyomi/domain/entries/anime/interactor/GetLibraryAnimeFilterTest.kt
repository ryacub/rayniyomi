package tachiyomi.domain.entries.anime.interactor

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.model.LibraryFilter

@Execution(ExecutionMode.CONCURRENT)
class GetLibraryAnimeFilterTest {

    private val animeRepository: AnimeRepository = mockk()
    private val interactor = GetLibraryAnime(animeRepository)

    private fun createLibraryAnime(
        id: Long = 1L,
        seenCount: Long = 0L,
        totalCount: Long = 10L,
        bookmarkCount: Long = 0L,
        statusCompleted: Boolean = false,
        fetchInterval: Int = 7,
    ): LibraryAnime {
        val anime = Anime.create().copy(
            id = id,
            status = if (statusCompleted) 2L else 1L,
            fetchInterval = fetchInterval,
        )
        return LibraryAnime(
            anime = anime,
            category = 0L,
            totalCount = totalCount,
            seenCount = seenCount,
            bookmarkCount = bookmarkCount,
            fillermarkCount = 0L,
            latestUpload = 0L,
            episodeFetchedAt = 0L,
            lastSeen = 0L,
        )
    }

    @Test
    fun `await with DISABLED filters returns all anime`() = runTest {
        val filterSlot = slot<LibraryFilter>()
        coEvery { animeRepository.getLibraryAnimeFiltered(capture(filterSlot)) } returns listOf(
            createLibraryAnime(id = 1L, seenCount = 0L),
            createLibraryAnime(id = 2L, seenCount = 5L),
        )

        val filter = LibraryFilter(
            filterUnread = TriState.DISABLED,
            filterStarted = TriState.DISABLED,
            filterBookmarked = TriState.DISABLED,
            filterCompleted = TriState.DISABLED,
            filterIntervalCustom = TriState.DISABLED,
        )

        val result = interactor.await(filter)

        result shouldHaveSize 2
    }

    @Test
    fun `await with ENABLED_IS unseen filter passes correct filter to repository`() = runTest {
        val filterSlot = slot<LibraryFilter>()
        coEvery { animeRepository.getLibraryAnimeFiltered(capture(filterSlot)) } returns listOf(
            createLibraryAnime(id = 1L, seenCount = 0L, totalCount = 5L),
        )

        val filter = LibraryFilter(
            filterUnread = TriState.ENABLED_IS,
            filterStarted = TriState.DISABLED,
            filterBookmarked = TriState.DISABLED,
            filterCompleted = TriState.DISABLED,
            filterIntervalCustom = TriState.DISABLED,
        )

        interactor.await(filter)

        filterSlot.captured.filterUnread shouldBe TriState.ENABLED_IS
    }

    @Test
    fun `await with ENABLED_IS started filter passes correct filter to repository`() = runTest {
        val filterSlot = slot<LibraryFilter>()
        coEvery { animeRepository.getLibraryAnimeFiltered(capture(filterSlot)) } returns listOf(
            createLibraryAnime(id = 2L, seenCount = 3L),
        )

        val filter = LibraryFilter(
            filterUnread = TriState.DISABLED,
            filterStarted = TriState.ENABLED_IS,
            filterBookmarked = TriState.DISABLED,
            filterCompleted = TriState.DISABLED,
            filterIntervalCustom = TriState.DISABLED,
        )

        interactor.await(filter)

        filterSlot.captured.filterStarted shouldBe TriState.ENABLED_IS
    }

    @Test
    fun `await with combined filters passes all filters to repository`() = runTest {
        val filterSlot = slot<LibraryFilter>()
        coEvery { animeRepository.getLibraryAnimeFiltered(capture(filterSlot)) } returns emptyList()

        val filter = LibraryFilter(
            filterUnread = TriState.ENABLED_IS,
            filterStarted = TriState.ENABLED_NOT,
            filterBookmarked = TriState.DISABLED,
            filterCompleted = TriState.ENABLED_IS,
            filterIntervalCustom = TriState.ENABLED_NOT,
        )

        val result = interactor.await(filter)

        result.shouldBeEmpty()
        filterSlot.captured.filterUnread shouldBe TriState.ENABLED_IS
        filterSlot.captured.filterStarted shouldBe TriState.ENABLED_NOT
        filterSlot.captured.filterCompleted shouldBe TriState.ENABLED_IS
    }
}
