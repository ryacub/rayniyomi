package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationRetryPolicyTest {

    private val policy = TranslationRetryPolicy()

    @Test
    fun `429 is transient`() {
        assertTrue(policy.isTransient(HttpException(429)))
    }

    @Test
    fun `5xx is transient`() {
        assertTrue(policy.isTransient(HttpException(500)))
        assertTrue(policy.isTransient(HttpException(503)))
        assertTrue(policy.isTransient(HttpException(599)))
    }

    @Test
    fun `4xx other than 429 is not transient`() {
        assertFalse(policy.isTransient(HttpException(400)))
        assertFalse(policy.isTransient(HttpException(401)))
        assertFalse(policy.isTransient(HttpException(404)))
    }

    @Test
    fun `a non-http error is not transient`() {
        assertFalse(policy.isTransient(RuntimeException("boom")))
    }

    @Test
    fun `succeeds without retrying when the first attempt works`() = runTest {
        var calls = 0

        val result = policy.execute(label = "page 1") {
            calls++
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `retries a transient failure and returns the later success`() = runTest {
        var calls = 0

        val result = policy.execute(label = "page 1") {
            if (calls++ == 0) throw HttpException(503)
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `gives up after three retries and rethrows the last error`() = runTest {
        var calls = 0

        val result = runCatching {
            policy.execute<String>(label = "page 1") {
                calls++
                throw HttpException(503)
            }
        }

        assertTrue(result.exceptionOrNull() is HttpException)
        assertEquals(4, calls)
    }

    @Test
    fun `rethrows a permanent failure on the first attempt`() = runTest {
        var retries = 0
        val result = runCatching {
            policy.execute(
                label = "page 1",
                onRetry = { retries++ },
            ) {
                throw HttpException(401)
            }
        }

        assertTrue(result.exceptionOrNull() is HttpException)
        assertEquals(0, retries)
    }

    @Test
    fun `backs off two then four then eight seconds`() = runTest {
        val startedAt = mutableListOf<Long>()
        val result = runCatching {
            policy.execute(label = "page 1") {
                startedAt += currentTime
                throw HttpException(503)
            }
        }
        assertTrue(result.isFailure)
        assertEquals(listOf(0L, 2_000L, 6_000L, 14_000L), startedAt)
    }

    @Test
    fun `reports each failed attempt to onRetry with its index`() = runTest {
        val attempts = mutableListOf<Int>()
        runCatching {
            policy.execute(
                label = "page 1",
                onRetry = { attempts += it },
            ) {
                throw HttpException(503)
            }
        }

        assertEquals(listOf(0, 1, 2), attempts)
    }

    @Test
    fun `rethrows cancellation without retrying`() = runTest {
        var calls = 0
        val result = runCatching {
            policy.execute<Unit>(label = "page 1") {
                calls++
                throw CancellationException("cancelled")
            }
        }

        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(1, calls)
    }

    @Test
    fun `honours a custom retry budget`() = runTest {
        val tightPolicy = TranslationRetryPolicy(maxRetries = 1)
        var calls = 0
        val result = runCatching {
            tightPolicy.execute<String>(label = "page 1") {
                calls++
                throw HttpException(503)
            }
        }

        assertTrue(result.isFailure)
        assertEquals(2, calls)
    }
}
