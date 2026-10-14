package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import eu.kanade.tachiyomi.util.system.WebViewUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CloudflareInterceptorTest {

    private val context = mockk<Context>(relaxed = true)
    private val cookieManager = mockk<AndroidCookieJar>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.getMainExecutor(any()) } returns mockk(relaxed = true)
        mockkObject(WebViewUtil)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
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
    fun `a challenge without WebView support returns the challenge response`() {
        every { WebViewUtil.supportsWebView(any()) } returns false
        val request = request()
        val challenge = challenge()
        val chain = chain(request, challenge)

        val result = interceptor().intercept(chain)

        assertSame(challenge, result)
        verify(exactly = 1) { chain.proceed(request) }
        verify(exactly = 0) { cookieManager.remove(any(), any(), any()) }
    }

    @Test
    fun `a same-host request on another thread is not blocked by a running solve`() {
        val interceptor = interceptor()
        val solveStarted = CountDownLatch(1)
        val finishSolve = CountDownLatch(1)
        every { WebViewUtil.supportsWebView(any()) } answers {
            solveStarted.countDown()
            finishSolve.await()
            false
        }
        val innerRequest = request("/token")
        val innerPassed = response(200)
        val innerChain = chain(innerRequest, innerPassed)
        val outerRequest = request("/outer")
        val outerPassed = response(200)
        val nestedCalls = Executors.newSingleThreadExecutor()
        var solver: Thread? = null
        val outerChain = mockk<Interceptor.Chain> {
            every { request() } returns outerRequest
            every { proceed(outerRequest) } answers {
                solver = thread { interceptor.intercept(chain(request("/page"), challenge())) }
                assertTrue(solveStarted.await(5, TimeUnit.SECONDS))
                val inner = nestedCalls.submit<Response> { interceptor.intercept(innerChain) }
                assertSame(innerPassed, inner.get(2, TimeUnit.SECONDS))
                outerPassed
            }
        }

        try {
            assertSame(outerPassed, interceptor.intercept(outerChain))
        } finally {
            finishSolve.countDown()
            solver?.join(5_000)
            nestedCalls.shutdownNow()
        }
    }

    @Test
    fun `a request that waited on a failed solve fails without its own solve`() {
        val interceptor = interceptor()
        val solveStarted = CountDownLatch(1)
        val finishSolve = CountDownLatch(1)
        every { WebViewUtil.supportsWebView(any()) } answers {
            solveStarted.countDown()
            finishSolve.await()
            error("challenge failed")
        }
        val solver = thread { runCatching { interceptor.intercept(chain(request("/first"), challenge())) } }
        assertTrue(solveStarted.await(5, TimeUnit.SECONDS))
        val waiterRequest = request("/second")
        val waiterChain = chain(waiterRequest, challenge(), challenge())
        val waiterCalls = Executors.newSingleThreadExecutor()

        try {
            val waiter = waiterCalls.submit<Response> { interceptor.intercept(waiterChain) }
            Thread.sleep(300)
            finishSolve.countDown()
            val failure = assertThrows(ExecutionException::class.java) { waiter.get(5, TimeUnit.SECONDS) }.cause

            assertInstanceOf(IOException::class.java, failure)
            assertInstanceOf(CloudflareBypassException::class.java, failure?.cause)
            verify(exactly = 1) { WebViewUtil.supportsWebView(any()) }
            verify(exactly = 2) { waiterChain.proceed(waiterRequest) }
        } finally {
            finishSolve.countDown()
            solver.join(5_000)
            waiterCalls.shutdownNow()
        }
    }

    private fun interceptor() = CloudflareInterceptor(context, cookieManager) { "test-agent" }

    private fun request(path: String = "/page") = Request.Builder().url("https://example.org$path").build()

    private fun chain(request: Request, vararg responses: Response) = mockk<Interceptor.Chain> {
        every { request() } returns request
        every { proceed(request) } returnsMany responses.toList()
    }

    private fun challenge() = response(403, "Server" to "cloudflare", "cf-mitigated" to "challenge")

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
