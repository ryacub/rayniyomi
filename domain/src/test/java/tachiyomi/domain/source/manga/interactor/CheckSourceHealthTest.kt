package tachiyomi.domain.source.manga.interactor

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaImpl
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.source.manga.model.SourceHealth
import tachiyomi.domain.source.manga.model.SourceHealthStatus
import tachiyomi.domain.source.manga.repository.SourceHealthRepository
import tachiyomi.domain.source.manga.service.MangaSourceManager

@Execution(ExecutionMode.CONCURRENT)
class CheckSourceHealthTest {

    private val sourceManager: MangaSourceManager = mockk()
    private val healthRepository: SourceHealthRepository = mockk(relaxUnitFun = true)
    private val interactor = CheckSourceHealth(sourceManager, healthRepository)

    private val testSourceId = 42L

    private fun createMangasPage(count: Int = 1): MangasPage {
        val mangas = List(count) {
            SMangaImpl().apply {
                url = "/manga/$it"
                title = "Manga $it"
            }
        }
        return MangasPage(mangas, hasNextPage = false)
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

    // --- Source is not CatalogueSource ---

    @Test
    fun `check returns UNKNOWN when source is not CatalogueSource`() = runTest {
        val plainSource: MangaSource = mockk()
        every { sourceManager.get(testSourceId) } returns plainSource

        val result = interactor.check(testSourceId)

        result.sourceId shouldBe testSourceId
        result.status shouldBe SourceHealthStatus.UNKNOWN
    }

    // --- Successful checks ---

    @Test
    fun `check returns HEALTHY on successful getPopularManga with results`() = runTest {
        val catalogueSource: CatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularManga(1) } returns createMangasPage(3)
        coEvery { healthRepository.get(testSourceId) } returns null

        val result = interactor.check(testSourceId)

        result.status shouldBe SourceHealthStatus.HEALTHY
        result.failureCount shouldBe 0
        result.lastError shouldBe null
        coVerify { healthRepository.upsert(match { it.status == SourceHealthStatus.HEALTHY }) }
    }

    @Test
    fun `check resets to HEALTHY after previous failures`() = runTest {
        val catalogueSource: CatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularManga(1) } returns createMangasPage(1)
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
        val catalogueSource: CatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularManga(1) } throws RuntimeException("Network error")
        coEvery { healthRepository.get(testSourceId) } returns null

        val result = interactor.check(testSourceId)

        result.status shouldBe SourceHealthStatus.DEGRADED
        result.failureCount shouldBe 1
        result.lastError shouldBe "Network error"
    }

    @Test
    fun `check returns DEGRADED on second consecutive failure`() = runTest {
        val catalogueSource: CatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularManga(1) } throws RuntimeException("Timeout")
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
        val catalogueSource: CatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularManga(1) } throws RuntimeException("Still broken")
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
        val catalogueSource: CatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularManga(1) } throws RuntimeException("Down")
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
        val catalogueSource: CatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularManga(1) } returns MangasPage(emptyList(), false)
        coEvery { healthRepository.get(testSourceId) } returns null

        val result = interactor.check(testSourceId)

        result.status shouldBe SourceHealthStatus.HEALTHY
        result.failureCount shouldBe 0
        result.lastError shouldBe null
    }

    @Test
    fun `check treats empty results page as healthy even with prior failures`() = runTest {
        val catalogueSource: CatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularManga(1) } returns MangasPage(emptyList(), false)
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
        val catalogueSource: CatalogueSource = mockk()
        every { sourceManager.get(testSourceId) } returns catalogueSource
        coEvery { catalogueSource.getPopularManga(1) } throws RuntimeException("Error")
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
