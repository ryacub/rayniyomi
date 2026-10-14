package eu.kanade.tachiyomi.network.interceptor

import android.util.Log
import android.webkit.CookieManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Drives the app's Cloudflare solve with a real WebView against a loopback server that answers like
 * a non-interactive Cloudflare challenge: its page sets `cf_clearance` after a delay and reloads.
 */
@RunWith(AndroidJUnit4::class)
class CloudflareChallengeAndroidTest {

    private lateinit var server: ChallengeServer
    private val network = Injekt.get<NetworkHelper>()

    @Before
    fun setUp() {
        clearCookies()
        server = ChallengeServer()
    }

    @After
    fun tearDown() {
        server.close()
        clearCookies()
    }

    private fun clearCookies() {
        val cleared = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            CookieManager.getInstance().removeAllCookies { cleared.countDown() }
        }
        assertTrue(cleared.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun aDelayedChallengeIsSolvedBeforeTheRequestIsRetried() {
        val startedAt = System.nanoTime()
        val response = network.client.newCall(Request.Builder().url(server.url("/page")).build()).execute()
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        val body = response.use { it.body.string() }
        Log.i(TAG, "elapsed=${elapsedMs}ms code=${response.code} requests=${server.requests}")

        assertEquals(200, response.code)
        assertEquals(SOLVED_BODY, body)
        assertTrue("solve returned before the challenge delay: ${elapsedMs}ms", elapsedMs >= CHALLENGE_DELAY_MS)
    }

    @Test
    fun concurrentChallengedRequestsShareOneSolve() {
        val calls = Executors.newFixedThreadPool(2)
        val responses = try {
            listOf("/first", "/second")
                .map { path -> calls.submit<Pair<Int, String>> { fetch(path) } }
                .map { it.get(60, TimeUnit.SECONDS) }
        } finally {
            calls.shutdownNow()
        }
        Log.i(TAG, "responses=$responses requests=${server.requests}")

        assertEquals(listOf(200 to SOLVED_BODY, 200 to SOLVED_BODY), responses)
        assertEquals(1, server.requests.count { it.webView && !it.solved && "favicon" !in it.line })
    }

    private fun fetch(path: String): Pair<Int, String> {
        return network.client.newCall(Request.Builder().url(server.url(path)).build()).execute()
            .use { it.code to it.body.string() }
    }

    private data class ServedRequest(val line: String, val webView: Boolean, val solved: Boolean)

    private class ChallengeServer : Closeable {

        private val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        val requests = CopyOnWriteArrayList<ServedRequest>()

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    thread(isDaemon = true) { serve(client) }
                }
            }
        }

        fun url(path: String) = "http://127.0.0.1:${socket.localPort}$path"

        private fun serve(client: Socket) {
            client.use {
                val reader = BufferedReader(InputStreamReader(it.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val headers = generateSequence { reader.readLine()?.takeIf(String::isNotEmpty) }
                    .map { line -> line.substringBefore(':').lowercase() to line.substringAfter(':').trim() }
                    .toList()
                val solved = headers.any { (name, value) -> name == "cookie" && "cf_clearance=" in value }
                // Chromium sends this header on navigations, OkHttp does not
                val webView = headers.any { (name, _) -> name == "upgrade-insecure-requests" }
                requests += ServedRequest(requestLine, webView, solved)

                val response = if (solved) {
                    "HTTP/1.1 200 OK\r\n" to SOLVED_BODY
                } else {
                    "HTTP/1.1 403 Forbidden\r\nServer: cloudflare\r\ncf-mitigated: challenge\r\n" to CHALLENGE_PAGE
                }
                val bytes = response.second.toByteArray()
                it.getOutputStream().apply {
                    write(
                        (
                            response.first +
                                "Content-Type: text/html; charset=utf-8\r\n" +
                                "Content-Length: ${bytes.size}\r\n" +
                                "Connection: close\r\n\r\n"
                            ).toByteArray(),
                    )
                    write(bytes)
                    flush()
                }
            }
        }

        override fun close() {
            runCatching { socket.close() }
        }
    }

    private companion object {
        const val TAG = "CloudflareChallengeTest"
        const val CHALLENGE_DELAY_MS = 3_000L
        const val SOLVED_BODY = "solved"
        val CHALLENGE_PAGE = """
            <html><body>Checking your browser<script>
            setTimeout(function () {
                document.cookie = "cf_clearance=granted; path=/";
                location.reload();
            }, $CHALLENGE_DELAY_MS);
            </script></body></html>
        """.trimIndent()
    }
}
