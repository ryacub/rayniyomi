package eu.kanade.tachiyomi.data.track

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.io.IOException

class TrackerRateLimitRetryTest {

    @Test
    fun `returns first response when not 429`() = runTest {
        var attempts = 0

        val result = retryOnceOn429Result {
            attempts++
            buildResponse(200)
        }

        result.isSuccess shouldBe true
        result.getOrThrow().code shouldBe 200
        attempts shouldBe 1
        testScheduler.currentTime shouldBe 0
    }

    @Test
    fun `retries once on 429 then succeeds`() = runTest {
        var attempts = 0

        val result = retryOnceOn429Result {
            attempts++
            when (attempts) {
                1 -> buildResponse(429, retryAfter = "2")
                else -> buildResponse(200)
            }
        }

        result.isSuccess shouldBe true
        result.getOrThrow().code shouldBe 200
        attempts shouldBe 2
        testScheduler.currentTime shouldBe 2_000
    }

    @Test
    fun `uses default delay when retry-after is missing`() = runTest {
        var attempts = 0

        val missingHeader = retryOnceOn429Result {
            attempts++
            when (attempts) {
                1 -> buildResponse(429)
                else -> buildResponse(200)
            }
        }

        missingHeader.isSuccess shouldBe true
        testScheduler.currentTime shouldBe 5_000
    }

    @Test
    fun `uses default delay when retry-after is invalid`() = runTest {
        var attempts = 0

        val invalidHeader = retryOnceOn429Result {
            attempts++
            when (attempts) {
                1 -> buildResponse(429, retryAfter = "oops")
                else -> buildResponse(200)
            }
        }

        invalidHeader.isSuccess shouldBe true
        testScheduler.currentTime shouldBe 5_000
    }

    @Test
    fun `caps delay at max wait seconds`() = runTest {
        var attempts = 0

        val result = retryOnceOn429Result {
            attempts++
            when (attempts) {
                1 -> buildResponse(429, retryAfter = "999")
                else -> buildResponse(200)
            }
        }

        result.isSuccess shouldBe true
        attempts shouldBe 2
        testScheduler.currentTime shouldBe 60_000
    }

    @Test
    fun `only retries once when second response is also 429`() = runTest {
        var attempts = 0

        val result = retryOnceOn429Result {
            attempts++
            buildResponse(429, retryAfter = if (attempts == 1) "1" else null)
        }

        result.isSuccess shouldBe true
        result.getOrThrow().code shouldBe 429
        attempts shouldBe 2
        testScheduler.currentTime shouldBe 1_000
    }

    @Test
    fun `closes first 429 response before retry`() = runTest {
        var attempts = 0
        val firstResponse = mockk<Response>(relaxed = true)
        every { firstResponse.code } returns 429
        every { firstResponse.header("Retry-After") } returns "0"

        val result = retryOnceOn429Result {
            attempts++
            when (attempts) {
                1 -> firstResponse
                else -> buildResponse(200)
            }
        }

        result.isSuccess shouldBe true
        verify(exactly = 1) { firstResponse.close() }
    }

    @Test
    fun `safe variant returns failure when request throws`() = runTest {
        val failure = IOException("boom")

        val result = retryOnceOn429Result {
            throw failure
        }

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IOException>()
    }

    @Test
    fun `throwing variant rethrows original failure`() = runTest {
        val failure = runCatching {
            retryOnceOn429OrThrow {
                throw IOException("boom")
            }
        }.exceptionOrNull()

        failure.shouldBeInstanceOf<IOException>()
    }

    private fun buildResponse(code: Int, retryAfter: String? = null): Response {
        val request = Request.Builder()
            .url("https://example.com")
            .build()

        val responseBuilder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body("{}".toResponseBody())

        if (retryAfter != null) {
            responseBuilder.header("Retry-After", retryAfter)
        }

        return responseBuilder.build()
    }
}
