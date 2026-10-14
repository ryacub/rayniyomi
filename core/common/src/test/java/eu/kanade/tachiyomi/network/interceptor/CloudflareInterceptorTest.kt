package eu.kanade.tachiyomi.network.interceptor

import android.content.Context
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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

    private fun interceptor() = CloudflareInterceptor(context, cookieManager) { "test-agent" }

    private fun response(code: Int, vararg headers: Pair<String, String>): Response {
        val builder = Response.Builder()
            .request(Request.Builder().url("https://example.org/page").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        return builder.build()
    }
}
