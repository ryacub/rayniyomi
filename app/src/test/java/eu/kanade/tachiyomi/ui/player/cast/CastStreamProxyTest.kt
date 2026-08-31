package eu.kanade.tachiyomi.ui.player.cast

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress

class CastStreamProxyTest {

    private lateinit var upstream: MockWebServer
    private lateinit var proxy: CastStreamProxy

    @BeforeEach
    fun setUp() {
        upstream = MockWebServer()
        upstream.start()
        proxy = CastStreamProxy(
            client = OkHttpClient(),
            addressProvider = { InetAddress.getLoopbackAddress() },
            tokenProvider = { "test-token" },
        )
    }

    @AfterEach
    fun tearDown() {
        proxy.stop()
        upstream.shutdown()
    }

    @Test
    fun `proxy forwards configured headers and response body`() {
        upstream.enqueue(MockResponse().setBody("episode"))
        val url = proxy.urlFor(
            upstream.url("/episode.mp4").toString(),
            Headers.headersOf("Referer", "https://example.com/", "User-Agent", "cast-test"),
        )

        val response = OkHttpClient().newCall(Request.Builder().url(url).build()).execute()

        response.use {
            assertEquals(200, it.code)
            assertEquals("episode", it.body.string())
        }
        val request = upstream.takeRequest()
        assertEquals("https://example.com/", request.getHeader("Referer"))
        assertEquals("cast-test", request.getHeader("User-Agent"))
    }

    @Test
    fun `proxy forwards range requests and partial responses`() {
        upstream.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes 2-4/7")
                .setBody("ips"),
        )
        val url = proxy.urlFor(upstream.url("/episode.mp4").toString(), Headers.headersOf("User-Agent", "cast-test"))

        val response = OkHttpClient()
            .newCall(Request.Builder().url(url).header("Range", "bytes=2-4").build())
            .execute()

        response.use {
            assertEquals(206, it.code)
            assertEquals("bytes 2-4/7", it.header("Content-Range"))
            assertEquals("ips", it.body.string())
        }
        assertEquals("bytes=2-4", upstream.takeRequest().getHeader("Range"))
    }

    @Test
    fun `proxy refuses an unknown token without contacting upstream`() {
        val url = proxy.urlFor(upstream.url("/episode.mp4").toString(), Headers.headersOf("User-Agent", "cast-test"))
            .replace("test-token", "wrong-token")

        val response = OkHttpClient().newCall(Request.Builder().url(url).build()).execute()

        response.use { assertEquals(404, it.code) }
        assertEquals(0, upstream.requestCount)
    }

    @Test
    fun `proxy closes its port and rejects later requests`() {
        upstream.enqueue(MockResponse().setBody("episode"))
        val url = proxy.urlFor(upstream.url("/episode.mp4").toString(), Headers.headersOf("User-Agent", "cast-test"))
        proxy.stop()

        assertThrows(Exception::class.java) {
            OkHttpClient().newCall(Request.Builder().url(url).build()).execute().use { }
        }
    }

    @Test
    fun `proxy refuses to start without a reachable local address`() {
        val noAddressProxy = CastStreamProxy(
            client = OkHttpClient(),
            addressProvider = { null },
        )

        assertThrows(IllegalStateException::class.java) {
            noAddressProxy.urlFor(upstream.url("/episode.mp4").toString(), Headers.headersOf("User-Agent", "cast-test"))
        }
    }
}
