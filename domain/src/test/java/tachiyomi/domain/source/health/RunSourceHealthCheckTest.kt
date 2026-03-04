package tachiyomi.domain.source.health

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.domain.source.manga.model.SourceHealth
import tachiyomi.domain.source.manga.model.SourceHealthStatus
import tachiyomi.domain.source.manga.repository.SourceHealthRepository
import kotlin.coroutines.cancellation.CancellationException

@Execution(ExecutionMode.CONCURRENT)
class RunSourceHealthCheckTest {

    private val healthRepository: SourceHealthRepository = mockk(relaxUnitFun = true)
    private val checker: SourceHealthChecker = mockk()
    private val runner = RunSourceHealthCheck(healthRepository)

    private val testSourceId = 42L

    // --- shouldSkip ---

    @Test
    fun `check returns UNKNOWN and skips probe when shouldSkip is true`() = runTest {
        every { checker.shouldSkip(testSourceId) } returns true

        val result = runner.check(testSourceId, checker)

        result.sourceId shouldBe testSourceId
        result.status shouldBe SourceHealthStatus.UNKNOWN
        result.failureCount shouldBe 0
        result.lastError shouldBe null
        coVerify(exactly = 0) { checker.probe(any()) }
        coVerify(exactly = 0) { healthRepository.upsert(any()) }
    }

    // --- Successful checks ---

    @Test
    fun `check returns HEALTHY on successful probe`() = runTest {
        every { checker.shouldSkip(testSourceId) } returns false
        coEvery { checker.probe(testSourceId) } returns Unit
        coEvery { healthRepository.get(testSourceId) } returns null

        val result = runner.check(testSourceId, checker)

        result.sourceId shouldBe testSourceId
        result.status shouldBe SourceHealthStatus.HEALTHY
        result.failureCount shouldBe 0
        result.lastError shouldBe null
        coVerify { healthRepository.upsert(match { it.status == SourceHealthStatus.HEALTHY && it.failureCount == 0 }) }
    }

    @Test
    fun `check resets to HEALTHY with zero failures after previous failures`() = runTest {
        every { checker.shouldSkip(testSourceId) } returns false
        coEvery { checker.probe(testSourceId) } returns Unit
        coEvery { healthRepository.get(testSourceId) } returns SourceHealth(
            sourceId = testSourceId,
            status = SourceHealthStatus.BROKEN,
            lastCheckedAt = 0L,
            failureCount = 5,
            lastError = "Previous error",
        )

        val result = runner.check(testSourceId, checker)

        result.status shouldBe SourceHealthStatus.HEALTHY
        result.failureCount shouldBe 0
        result.lastError shouldBe null
    }

    // --- Failure states ---

    @Test
    fun `check returns DEGRADED on first failure`() = runTest {
        every { checker.shouldSkip(testSourceId) } returns false
        coEvery { checker.probe(testSourceId) } throws RuntimeException("Network error")
        coEvery { healthRepository.get(testSourceId) } returns null

        val result = runner.check(testSourceId, checker)

        result.status shouldBe SourceHealthStatus.DEGRADED
        result.failureCount shouldBe 1
        result.lastError shouldBe "Network error"
    }

    @Test
    fun `check returns DEGRADED on second consecutive failure`() = runTest {
        every { checker.shouldSkip(testSourceId) } returns false
        coEvery { checker.probe(testSourceId) } throws RuntimeException("Still down")
        coEvery { healthRepository.get(testSourceId) } returns SourceHealth(
            sourceId = testSourceId,
            status = SourceHealthStatus.DEGRADED,
            lastCheckedAt = 0L,
            failureCount = 1,
            lastError = "Previous error",
        )

        val result = runner.check(testSourceId, checker)

        result.status shouldBe SourceHealthStatus.DEGRADED
        result.failureCount shouldBe 2
        result.lastError shouldBe "Still down"
    }

    @Test
    fun `check transitions to BROKEN at failure threshold`() = runTest {
        every { checker.shouldSkip(testSourceId) } returns false
        coEvery { checker.probe(testSourceId) } throws RuntimeException("Broken")
        coEvery { healthRepository.get(testSourceId) } returns SourceHealth(
            sourceId = testSourceId,
            status = SourceHealthStatus.DEGRADED,
            lastCheckedAt = 0L,
            failureCount = 2,
            lastError = "Previous error",
        )

        val result = runner.check(testSourceId, checker)

        result.status shouldBe SourceHealthStatus.BROKEN
        result.failureCount shouldBe 3
        result.lastError shouldBe "Broken"
    }

    @Test
    fun `check stays BROKEN after exceeding failure threshold`() = runTest {
        every { checker.shouldSkip(testSourceId) } returns false
        coEvery { checker.probe(testSourceId) } throws RuntimeException("Still broken")
        coEvery { healthRepository.get(testSourceId) } returns SourceHealth(
            sourceId = testSourceId,
            status = SourceHealthStatus.BROKEN,
            lastCheckedAt = 0L,
            failureCount = 5,
            lastError = "Previous error",
        )

        val result = runner.check(testSourceId, checker)

        result.status shouldBe SourceHealthStatus.BROKEN
        result.failureCount shouldBe 6
    }

    // --- Timeout ---

    @Test
    fun `check returns DEGRADED when probe exceeds timeout`() = runTest {
        every { checker.shouldSkip(testSourceId) } returns false
        coEvery { checker.probe(testSourceId) } coAnswers { delay(15_000L) }
        coEvery { healthRepository.get(testSourceId) } returns null

        val result = runner.check(testSourceId, checker)

        result.status shouldBe SourceHealthStatus.DEGRADED
        result.failureCount shouldBe 1
        result.lastError shouldBe "Check timed out (10s)"
    }

    // --- CancellationException propagation ---

    @Test
    fun `CancellationException propagates and is not treated as failure`() = runTest {
        every { checker.shouldSkip(testSourceId) } returns false
        coEvery { checker.probe(testSourceId) } throws CancellationException("Job cancelled")
        coEvery { healthRepository.get(testSourceId) } returns null

        var propagated = false
        try {
            runner.check(testSourceId, checker)
        } catch (e: CancellationException) {
            propagated = true
        }

        propagated shouldBe true
        coVerify(exactly = 0) { healthRepository.upsert(any()) }
    }

    // --- Repository interactions ---

    @Test
    fun `check upserts DEGRADED health record on failure`() = runTest {
        every { checker.shouldSkip(testSourceId) } returns false
        coEvery { checker.probe(testSourceId) } throws RuntimeException("Error")
        coEvery { healthRepository.get(testSourceId) } returns null

        runner.check(testSourceId, checker)

        coVerify {
            healthRepository.upsert(
                match { it.status == SourceHealthStatus.DEGRADED && it.failureCount == 1 },
            )
        }
    }
}
