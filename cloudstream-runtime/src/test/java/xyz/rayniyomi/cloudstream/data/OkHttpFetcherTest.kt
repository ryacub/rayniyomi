package xyz.rayniyomi.cloudstream.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.junit.jupiter.api.Test

class OkHttpFetcherTest {

    private fun fetcher(code: Int = 200, body: String, declaredLength: Long = -1L) = OkHttpFetcher(
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(Buffer().writeUtf8(body).asResponseBody(null, declaredLength))
                    .build()
            }
            .build(),
        maxBytes = 8,
    )

    @Test
    fun `a body within the limit is returned`() = runTest {
        fetcher(body = "12345678").text("https://repo.test/plugins.json") shouldBe "12345678"
    }

    @Test
    fun `a body without a declared length is still limited`() = runTest {
        shouldThrow<IllegalStateException> { fetcher(body = "123456789").bytes("https://repo.test/a.cs3") }
    }

    @Test
    fun `a declared length over the limit is refused`() = runTest {
        shouldThrow<IllegalStateException> {
            fetcher(body = "1", declaredLength = 9).bytes("https://repo.test/a.cs3")
        }
    }

    @Test
    fun `an unsuccessful response is refused`() = runTest {
        shouldThrow<IllegalStateException> { fetcher(code = 404, body = "").text("https://repo.test/repo.json") }
    }
}
