package tachiyomi.domain.source.anime.interactor

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnimeImpl
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.model.SourceHealth
import tachiyomi.domain.source.manga.model.SourceHealthStatus
import tachiyomi.domain.source.manga.repository.SourceHealthRepository

@Execution(ExecutionMode.CONCURRENT)
class CheckAnimeSourceHealthTest {

    private val sourceManager: AnimeSourceManager = mockk()
    private val healthRepository: SourceHealthRepository = mockk(relaxUnitFun = true)
    private val interactor = CheckAnimeSourceHealth(sourceManager, healthRepository)

    private val testSourceId = 42L

    private fun createAnimesPage(count: Int = 1): AnimesPage {
        val animes = List(count) {
            SAnimeImpl().apply {
                url = "/anime/$it"
                title = "Anime $it"
            }
        }
        return AnimesPage(animes, hasNextPage = false)
    }

    // --- Source not available ---

    @Test
    fun `check returns UNKNOWN when source is null`() = runTest {
        every { sourceManager.get(testSourceId) } returns null

        val result = interactor.check(testSourceId)

        result.sourceId shouldBe testSourceId
        result.status shouldBe SourceHealthStatus.UNKNOWN
        result.failureCount shouldBe 0
    }

    // --- Source is not AnimeCatalogueSource ---

    @Test
    fun `check returns UNKNOWN when source is not AnimeCatalogueSource`() = runTest {
        val plainSource: AnimeSource = mockk()
        every { sourceManager.get(testSourceId) } returns plainSource

        val result = interactor.check(testSourceId)

        result.sourceId shouldBe testSourceId
        result.status shouldBe SourceHealthStatus.UNKNOWN
    }

    // --- Successful checks ---

    @Test
    fun `check returns HEALTHY on successful getPopularAnime with results`() = runTest {
        val catalogueSource: AnimeCatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularAnime(1) } returns createAnimesPage(3)
        coEvery { healthRepository.get(testSourceId) } returns null

        val result = interactor.check(testSourceId)

        result.status shouldBe SourceHealthStatus.HEALTHY
        result.failureCount shouldBe 0
        result.lastError shouldBe null
        coVerify { healthRepository.upsert(match { it.status == SourceHealthStatus.HEALTHY }) }
    }

    @Test
    fun `check resets to HEALTHY after previous failures`() = runTest {
        val catalogueSource: AnimeCatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularAnime(1) } returns createAnimesPage(1)
        coEvery { healthRepository.get(testSourceId) } returns SourceHealth(
            sourceId = testSourceId,
            status = SourceHealthStatus.BROKEN,
            lastCheckedAt = 0L,
            failureCount = 5,
            lastError = "Previous error",
        )

        val result = interactor.check(testSourceId)

        result.status shouldBe SourceHealthStatus.HEALTHY
        result.failureCount shouldBe 0
        result.lastError shouldBe null
    }

    // --- Failure states ---

    @Test
    fun `check returns DEGRADED on first failure`() = runTest {
        val catalogueSource: AnimeCatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularAnime(1) } throws RuntimeException("Network error")
        coEvery { healthRepository.get(testSourceId) } returns null

        val result = interactor.check(testSourceId)

        result.status shouldBe SourceHealthStatus.DEGRADED
        result.failureCount shouldBe 1
        result.lastError shouldBe "Network error"
    }

    @Test
    fun `check returns DEGRADED on second consecutive failure`() = runTest {
        val catalogueSource: AnimeCatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularAnime(1) } throws RuntimeException("Timeout")
        coEvery { healthRepository.get(testSourceId) } returns SourceHealth(
            sourceId = testSourceId,
            status = SourceHealthStatus.DEGRADED,
            lastCheckedAt = 0L,
            failureCount = 1,
            lastError = "Previous error",
        )

        val result = interactor.check(testSourceId)

        result.status shouldBe SourceHealthStatus.DEGRADED
        result.failureCount shouldBe 2
        result.lastError shouldBe "Timeout"
    }

    @Test
    fun `check transitions to BROKEN after three consecutive failures`() = runTest {
        val catalogueSource: AnimeCatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularAnime(1) } throws RuntimeException("Still broken")
        coEvery { healthRepository.get(testSourceId) } returns SourceHealth(
            sourceId = testSourceId,
            status = SourceHealthStatus.DEGRADED,
            lastCheckedAt = 0L,
            failureCount = 2,
            lastError = "Previous error",
        )

        val result = interactor.check(testSourceId)

        result.status shouldBe SourceHealthStatus.BROKEN
        result.failureCount shouldBe 3
        result.lastError shouldBe "Still broken"
    }

    @Test
    fun `check stays BROKEN after more than three failures`() = runTest {
        val catalogueSource: AnimeCatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularAnime(1) } throws RuntimeException("Down")
        coEvery { healthRepository.get(testSourceId) } returns SourceHealth(
            sourceId = testSourceId,
            status = SourceHealthStatus.BROKEN,
            lastCheckedAt = 0L,
            failureCount = 5,
            lastError = "Previous error",
        )

        val result = interactor.check(testSourceId)

        result.status shouldBe SourceHealthStatus.BROKEN
        result.failureCount shouldBe 6
    }

    // --- Empty results page ---

    @Test
    fun `check treats empty results page as healthy`() = runTest {
        val catalogueSource: AnimeCatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularAnime(1) } returns AnimesPage(emptyList(), false)
        coEvery { healthRepository.get(testSourceId) } returns null

        val result = interactor.check(testSourceId)

        result.status shouldBe SourceHealthStatus.HEALTHY
        result.failureCount shouldBe 0
        result.lastError shouldBe null
    }

    @Test
    fun `check treats empty results page as healthy even with prior failures`() = runTest {
        val catalogueSource: AnimeCatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularAnime(1) } returns AnimesPage(emptyList(), false)
        coEvery { healthRepository.get(testSourceId) } returns SourceHealth(
            sourceId = testSourceId,
            status = SourceHealthStatus.DEGRADED,
            lastCheckedAt = 0L,
            failureCount = 2,
            lastError = "Previous error",
        )

        val result = interactor.check(testSourceId)

        result.status shouldBe SourceHealthStatus.HEALTHY
        result.failureCount shouldBe 0
        result.lastError shouldBe null
    }

    // --- Repository interactions ---

    @Test
    fun `check upserts health record on failure`() = runTest {
        val catalogueSource: AnimeCatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularAnime(1) } throws RuntimeException("Error")
        coEvery { healthRepository.get(testSourceId) } returns null

        interactor.check(testSourceId)

        coVerify { healthRepository.upsert(match { it.status == SourceHealthStatus.DEGRADED && it.failureCount == 1 }) }
    }

    @Test
    fun `check does not upsert when source is null`() = runTest {
        every { sourceManager.get(testSourceId) } returns null

        interactor.check(testSourceId)

        coVerify(exactly = 0) { healthRepository.upsert(any()) }
    }
}
