package eu.kanade.tachiyomi.util.system

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * End-to-end checks on what actually reaches a server, covering both paths the default user agent
 * feeds: plain OkHttp source requests and WebView navigations.
 */
@RunWith(AndroidJUnit4::class)
class UserAgentOnTheWireAndroidTest {

    private lateinit var server: RecordingHttpServer

    @Before
    fun startServer() {
        server = RecordingHttpServer()
    }

    @After
    fun stopServer() {
        server.close()
    }

    @Test
    fun okHttpSourceRequests_carryTheChromeDefaultUserAgent() {
        val network = Injekt.get<NetworkHelper>()
        val expected = network.defaultUserAgentProvider()

        network.client.newCall(Request.Builder().url(server.url).build()).execute().close()

        val request = server.awaitFirstRequest()
        assertEquals(expected, request.userAgent)
        assertTrue(
            "Default user agent is no longer Chrome-shaped: $expected",
            Regex("""Chrome/\d+""").containsMatchIn(expected),
        )
    }

    @Test
    fun webViewNavigations_sendClientHintsMatchingTheSpoofedUserAgent() {
        val userAgent = Injekt.get<NetworkHelper>().defaultUserAgentProvider()

        loadInWebView(userAgent)

        val request = server.awaitFirstRequest()
        assertEquals(userAgent, request.userAgent)

        val secChUa = request.secChUa
        assertNotNull("WebView sent no Sec-CH-UA over a loopback origin", secChUa)
        assertTrue(
            "Sec-CH-UA still advertises the real WebView brand: $secChUa",
            "Android WebView" !in secChUa!!,
        )
        assertTrue(
            "Sec-CH-UA does not claim Google Chrome: $secChUa",
            "Google Chrome" in secChUa,
        )
        val major = Regex("""Chrome/(\d+)""").find(userAgent)!!.groupValues[1]
        assertTrue(
            "Sec-CH-UA version does not match the user agent major $major: $secChUa",
            """v="$major"""" in secChUa,
        )
    }

    @Test
    fun webViewNavigations_withNonChromeUserAgentLeaveClientHintsUntouched() {
        loadInWebView(FIREFOX_USER_AGENT)

        val request = server.awaitFirstRequest()
        assertEquals(FIREFOX_USER_AGENT, request.userAgent)

        // Documents the known, deliberate residual mismatch: a non-Chrome user agent keeps the real
        // WebView client hints rather than gaining fabricated ones.
        val secChUa = request.secChUa
        assertTrue(
            "Expected untouched client hints for a non-Chrome user agent, got $secChUa",
            secChUa == null || "Android WebView" in secChUa,
        )
    }

    private fun loadInWebView(userAgent: String) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            WebView(ApplicationProvider.getApplicationContext()).apply {
                setDefaultSettings()
                setUserAgent(userAgent)
                loadUrl(server.url)
            }
        }
    }

    private companion object {
        const val FIREFOX_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0"
    }
}
