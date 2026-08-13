package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class SourceFiltersTest {

    @Test
    fun `manga source filters pass through when the extension works`() {
        val filters = FilterList()
        val source = mockk<CatalogueSource> {
            every { getFilterList() } returns filters
        }

        source.getFilterListOrNull() shouldBe filters
    }

    @Test
    fun `manga source filters are null when the extension fails to link`() {
        val source = mockk<CatalogueSource> {
            every { name } returns "Example"
            every { getFilterList() } throws NoSuchMethodError("runBlockingK")
        }

        source.getFilterListOrNull() shouldBe null
    }

    @Test
    fun `manga source filters are null when the extension throws`() {
        val source = mockk<CatalogueSource> {
            every { name } returns "Example"
            every { getFilterList() } throws IllegalStateException("boom")
        }

        source.getFilterListOrNull() shouldBe null
    }

    @Test
    fun `manga source filter failures stay retryable`() {
        var attempts = 0
        val source = mockk<CatalogueSource> {
            every { name } returns "Example"
            every { getFilterList() } answers {
                attempts++
                throw LinkageError("incompatible host API")
            }
        }

        repeat(2) { source.getFilterListOrNull() shouldBe null }

        attempts shouldBe 2
    }

    @Test
    fun `anime source filters pass through when the extension works`() {
        val filters = AnimeFilterList()
        val source = mockk<AnimeCatalogueSource> {
            every { getFilterList() } returns filters
        }

        source.getFilterListOrNull() shouldBe filters
    }

    @Test
    fun `anime source filters are null when the extension fails to link`() {
        val source = mockk<AnimeCatalogueSource> {
            every { name } returns "Example"
            every { getFilterList() } throws NoSuchMethodError("runBlockingK")
        }

        source.getFilterListOrNull() shouldBe null
    }

    @Test
    fun `anime source filters are null when the extension throws`() {
        val source = mockk<AnimeCatalogueSource> {
            every { name } returns "Example"
            every { getFilterList() } throws IllegalStateException("boom")
        }

        source.getFilterListOrNull() shouldBe null
    }

    @Test
    fun `cancellation is not swallowed`() {
        val source = mockk<CatalogueSource> {
            every { name } returns "Example"
            every { getFilterList() } throws kotlinx.coroutines.CancellationException("cancelled")
        }

        runCatching { source.getFilterListOrNull() }.isFailure shouldBe true
    }
}
