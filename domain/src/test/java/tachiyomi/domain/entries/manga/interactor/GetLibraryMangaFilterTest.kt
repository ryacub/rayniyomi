package tachiyomi.domain.entries.manga.interactor

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
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.model.LibraryFilter

@Execution(ExecutionMode.CONCURRENT)
class GetLibraryMangaFilterTest {

    private val mangaRepository: MangaRepository = mockk()
    private val interactor = GetLibraryManga(mangaRepository)

    private fun createLibraryManga(
        id: Long = 1L,
        readCount: Long = 0L,
        totalChapters: Long = 10L,
        bookmarkCount: Long = 0L,
        statusCompleted: Boolean = false,
        fetchInterval: Int = 7,
    ): LibraryManga {
        val manga = Manga.create().copy(
            id = id,
            status = if (statusCompleted) 2L else 1L,
            fetchInterval = fetchInterval,
        )
        return LibraryManga(
            manga = manga,
            category = 0L,
            totalChapters = totalChapters,
            readCount = readCount,
            bookmarkCount = bookmarkCount,
            latestUpload = 0L,
            chapterFetchedAt = 0L,
            lastRead = 0L,
        )
    }

    @Test
    fun `await with DISABLED filters returns all manga`() = runTest {
        val filterSlot = slot<LibraryFilter>()
        coEvery { mangaRepository.getLibraryMangaFiltered(capture(filterSlot)) } returns listOf(
            createLibraryManga(id = 1L, readCount = 0L),
            createLibraryManga(id = 2L, readCount = 5L),
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
    fun `await with ENABLED_IS unread filter passes correct filter to repository`() = runTest {
        val filterSlot = slot<LibraryFilter>()
        coEvery { mangaRepository.getLibraryMangaFiltered(capture(filterSlot)) } returns listOf(
            createLibraryManga(id = 1L, readCount = 0L, totalChapters = 5L),
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
        coEvery { mangaRepository.getLibraryMangaFiltered(capture(filterSlot)) } returns listOf(
            createLibraryManga(id = 2L, readCount = 3L),
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
    fun `await with ENABLED_IS bookmarked filter passes correct filter to repository`() = runTest {
        val filterSlot = slot<LibraryFilter>()
        coEvery { mangaRepository.getLibraryMangaFiltered(capture(filterSlot)) } returns listOf(
            createLibraryManga(id = 3L, bookmarkCount = 2L),
        )

        val filter = LibraryFilter(
            filterUnread = TriState.DISABLED,
            filterStarted = TriState.DISABLED,
            filterBookmarked = TriState.ENABLED_IS,
            filterCompleted = TriState.DISABLED,
            filterIntervalCustom = TriState.DISABLED,
        )

        interactor.await(filter)

        filterSlot.captured.filterBookmarked shouldBe TriState.ENABLED_IS
    }

    @Test
    fun `await with ENABLED_IS completed filter passes correct filter to repository`() = runTest {
        val filterSlot = slot<LibraryFilter>()
        coEvery { mangaRepository.getLibraryMangaFiltered(capture(filterSlot)) } returns listOf(
            createLibraryManga(id = 4L, statusCompleted = true),
        )

        val filter = LibraryFilter(
            filterUnread = TriState.DISABLED,
            filterStarted = TriState.DISABLED,
            filterBookmarked = TriState.DISABLED,
            filterCompleted = TriState.ENABLED_IS,
            filterIntervalCustom = TriState.DISABLED,
        )

        interactor.await(filter)

        filterSlot.captured.filterCompleted shouldBe TriState.ENABLED_IS
    }

    @Test
    fun `await with ENABLED_NOT unread filter passes correct filter to repository`() = runTest {
        val filterSlot = slot<LibraryFilter>()
        coEvery { mangaRepository.getLibraryMangaFiltered(capture(filterSlot)) } returns listOf(
            createLibraryManga(id = 5L, readCount = 10L, totalChapters = 10L),
        )

        val filter = LibraryFilter(
            filterUnread = TriState.ENABLED_NOT,
            filterStarted = TriState.DISABLED,
            filterBookmarked = TriState.DISABLED,
            filterCompleted = TriState.DISABLED,
            filterIntervalCustom = TriState.DISABLED,
        )

        interactor.await(filter)

        filterSlot.captured.filterUnread shouldBe TriState.ENABLED_NOT
    }

    @Test
    fun `await with combined filters passes all filters to repository`() = runTest {
        val filterSlot = slot<LibraryFilter>()
        coEvery { mangaRepository.getLibraryMangaFiltered(capture(filterSlot)) } returns emptyList()

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
        filterSlot.captured.filterBookmarked shouldBe TriState.DISABLED
        filterSlot.captured.filterCompleted shouldBe TriState.ENABLED_IS
        filterSlot.captured.filterIntervalCustom shouldBe TriState.ENABLED_NOT
    }
}
