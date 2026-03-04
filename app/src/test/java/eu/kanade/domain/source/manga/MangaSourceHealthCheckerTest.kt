package eu.kanade.domain.source.manga

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.MangasPage
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.source.manga.service.MangaSourceManager

@Execution(ExecutionMode.CONCURRENT)
class MangaSourceHealthCheckerTest {

    private val sourceManager: MangaSourceManager = mockk()
    private val checker = MangaSourceHealthChecker(sourceManager)

    private val testSourceId = 42L
    private val localSourceId = 0L // LocalMangaSource.ID

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
    fun `shouldSkip returns true when source is not CatalogueSource`() {
        val plainSource: MangaSource = mockk()
        every { sourceManager.get(testSourceId) } returns plainSource

        val result = checker.shouldSkip(testSourceId)

        result shouldBe true
    }

    @Test
    fun `shouldSkip returns false for CatalogueSource`() {
        val catalogueSource: CatalogueSource = mockk()
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
    fun `probe completes without throwing when CatalogueSource returns page`() = runTest {
        val catalogueSource: CatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularManga(1) } returns MangasPage(emptyList(), false)

        checker.probe(testSourceId) // should not throw
    }
}
