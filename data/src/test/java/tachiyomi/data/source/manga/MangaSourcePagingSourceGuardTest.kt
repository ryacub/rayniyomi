package tachiyomi.data.source.manga

import androidx.paging.PagingSource
import eu.kanade.tachiyomi.source.CatalogueSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.util.lang.SourceLinkageException

/**
 * Crashlytics 3f669105bfc9f463ca2254ee25db0d5d: a defective extension raised NoSuchMethodError from
 * getPopularManga and the process stopped. `load` caught only Exception, and LinkageError is an
 * Error. Browse must report the fault instead.
 */
class MangaSourcePagingSourceGuardTest {

    private val refresh = PagingSource.LoadParams.Refresh<Long>(null, 1, false)

    @Test
    fun `a LinkageError from the source becomes an error result`() {
        val source = mockk<CatalogueSource>(relaxed = true) {
            every { name } returns "Broken Source"
            coEvery { getPopularManga(any()) } throws NoSuchMethodError("runBlockingK\$default")
        }

        val result = runBlocking { SourcePopularPagingSource(source).load(refresh) }

        result.shouldBeInstanceOf<PagingSource.LoadResult.Error<Long, *>>()
        val throwable = result.throwable
        throwable.shouldBeInstanceOf<SourceLinkageException>()
        throwable.sourceName shouldBe "Broken Source"
        throwable.cause.shouldBeInstanceOf<NoSuchMethodError>()
    }

    @Test
    fun `an unreadable source name still yields an error result`() {
        val source = mockk<CatalogueSource>(relaxed = true) {
            every { name } throws NoSuchMethodError("name")
            coEvery { getPopularManga(any()) } throws NoSuchMethodError("runBlockingK\$default")
        }

        val result = runBlocking { SourcePopularPagingSource(source).load(refresh) }

        result.shouldBeInstanceOf<PagingSource.LoadResult.Error<Long, *>>()
        val throwable = result.throwable
        throwable.shouldBeInstanceOf<SourceLinkageException>()
        throwable.sourceName shouldBe null
    }
}
