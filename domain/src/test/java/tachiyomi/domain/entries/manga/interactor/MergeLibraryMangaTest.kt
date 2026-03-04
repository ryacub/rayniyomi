package tachiyomi.domain.entries.manga.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.entries.manga.repository.MangaRepository

@Execution(ExecutionMode.CONCURRENT)
class MergeLibraryMangaTest {

    private val mangaRepository: MangaRepository = mockk()
    private val interactor = MergeLibraryManga(mangaRepository)

    @Test
    fun `await delegates to repository mergeEntries`() = runTest {
        coEvery { mangaRepository.mergeEntries(keepId = 1L, deleteId = 2L) } returns Unit

        interactor.await(keepId = 1L, deleteId = 2L)

        coVerify(exactly = 1) { mangaRepository.mergeEntries(keepId = 1L, deleteId = 2L) }
    }

    @Test
    fun `await passes keepId and deleteId in correct order`() = runTest {
        var capturedKeep: Long? = null
        var capturedDelete: Long? = null

        coEvery { mangaRepository.mergeEntries(any(), any()) } answers {
            capturedKeep = firstArg()
            capturedDelete = secondArg()
        }

        interactor.await(keepId = 10L, deleteId = 99L)

        capturedKeep shouldBe 10L
        capturedDelete shouldBe 99L
    }
}
