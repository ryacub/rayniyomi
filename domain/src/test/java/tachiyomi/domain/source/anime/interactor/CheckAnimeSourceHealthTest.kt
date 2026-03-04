package tachiyomi.domain.source.anime.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.source.health.RunSourceHealthCheck
import tachiyomi.domain.source.health.SourceHealthChecker
import tachiyomi.domain.source.manga.model.SourceHealth
import tachiyomi.domain.source.manga.model.SourceHealthStatus

@Execution(ExecutionMode.CONCURRENT)
class CheckAnimeSourceHealthTest {

    private val runner: RunSourceHealthCheck = mockk()
    private val checker: SourceHealthChecker = mockk()
    private val interactor = CheckAnimeSourceHealth(runner, checker)

    private val testSourceId = 42L

    @Test
    fun `check delegates to runner with correct arguments`() = runTest {
        val expectedHealth = SourceHealth(
            sourceId = testSourceId,
            status = SourceHealthStatus.HEALTHY,
            lastCheckedAt = 0L,
            failureCount = 0,
            lastError = null,
        )
        coEvery { runner.check(testSourceId, checker) } returns expectedHealth

        val result = interactor.check(testSourceId)

        result shouldBe expectedHealth
        coVerify(exactly = 1) { runner.check(testSourceId, checker) }
    }
}
