package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

class CloudflareInterceptorTest {

    private val context = mockk<Context>(relaxed = true)
    private val cookieManager = mockk<AndroidCookieJar>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.getMainExecutor(any()) } returns mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `a Cloudflare response marked as a challenge is intercepted`() {
        val response = response(403, "Server" to "cloudflare", "cf-mitigated" to "challenge")

        assertTrue(interceptor().shouldIntercept(response))
    }

    @Test
    fun `a Cloudflare 403 without the challenge marker is not intercepted`() {
        val response = response(403, "Server" to "cloudflare")

        assertFalse(interceptor().shouldIntercept(response))
    }

    @Test
    fun `a request whose challenge was cleared while it waited does not start a solve`() {
        val interceptor = interceptor()
        val request = request()
        val passed = response(200)
        val chain = chain(request, challenge(), passed)
        every { cookieManager.get(request.url) } returnsMany listOf(emptyList(), listOf(clearance(request)))

        val result = interceptor.intercept(chain)

        assertSame(passed, result)
        verify(exactly = 0) { cookieManager.remove(any(), any(), any()) }
    }

    @Test
    fun `a nested request to the same host can solve while the outer request waits`() {
        val interceptor = interceptor()
        val outerRequest = request("/outer")
        val innerRequest = request("/token")
        val innerPassed = response(200)
        val innerChain = chain(innerRequest, challenge(), innerPassed)
        every { cookieManager.get(innerRequest.url) } returnsMany listOf(emptyList(), listOf(clearance(innerRequest)))
        val outerPassed = response(200)
        val outerChain = mockk<Interceptor.Chain> {
            every { request() } returns outerRequest
            every { proceed(outerRequest) } answers {
                assertSame(innerPassed, interceptor.intercept(innerChain))
                outerPassed
            }
        }

        val result = assertTimeoutPreemptively(Duration.ofSeconds(5)) { interceptor.intercept(outerChain) }

        assertSame(outerPassed, result)
    }

    private fun interceptor() = CloudflareInterceptor(context, cookieManager) { "test-agent" }

    private fun request(path: String = "/page") = Request.Builder().url("https://example.org$path").build()

    private fun chain(request: Request, vararg responses: Response) = mockk<Interceptor.Chain> {
        every { request() } returns request
        every { proceed(request) } returnsMany responses.toList()
    }

    private fun challenge() = response(403, "Server" to "cloudflare", "cf-mitigated" to "challenge")

    private fun clearance(request: Request) = Cookie.Builder()
        .domain(request.url.host)
        .name("cf_clearance")
        .value("solved")
        .build()

    private fun response(code: Int, vararg headers: Pair<String, String>): Response {
        val builder = Response.Builder()
            .request(request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        return builder.build()
    }
}
