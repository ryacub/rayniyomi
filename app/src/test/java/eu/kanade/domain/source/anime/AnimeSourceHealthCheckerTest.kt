package eu.kanade.domain.source.anime

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.source.anime.service.AnimeSourceManager

@Execution(ExecutionMode.CONCURRENT)
class AnimeSourceHealthCheckerTest {

    private val sourceManager: AnimeSourceManager = mockk()
    private val checker = AnimeSourceHealthChecker(sourceManager)

    private val testSourceId = 42L
    private val localSourceId = 0L // LocalAnimeSource.ID

    // --- shouldSkip ---

    @Test
    fun `shouldSkip returns true for local source ID without calling sourceManager`() {
        // No stubbing needed — short-circuit prevents sourceManager.get(0L)
        val result = checker.shouldSkip(localSourceId)

        result shouldBe true
    }

    @Test
    fun `shouldSkip returns true when source is null`() {
        every { sourceManager.get(testSourceId) } returns null

        val result = checker.shouldSkip(testSourceId)

        result shouldBe true
    }

    @Test
    fun `shouldSkip returns true when source is not AnimeCatalogueSource`() {
        val plainSource: AnimeSource = mockk()
        every { sourceManager.get(testSourceId) } returns plainSource

        val result = checker.shouldSkip(testSourceId)

        result shouldBe true
    }

    @Test
    fun `shouldSkip returns false for AnimeCatalogueSource`() {
        val catalogueSource: AnimeCatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource

        val result = checker.shouldSkip(testSourceId)

        result shouldBe false
    }

    // --- probe ---

    @Test
    fun `probe throws IllegalStateException when source is not available`() = runTest {
        every { sourceManager.get(testSourceId) } returns null

        var threw = false
        try {
            checker.probe(testSourceId)
        } catch (e: IllegalStateException) {
            threw = true
        }

        threw shouldBe true
    }

    @Test
    fun `probe completes without throwing when AnimeCatalogueSource returns page`() = runTest {
        val catalogueSource: AnimeCatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularAnime(1) } returns AnimesPage(emptyList(), false)

        checker.probe(testSourceId) // should not throw
    }
}
