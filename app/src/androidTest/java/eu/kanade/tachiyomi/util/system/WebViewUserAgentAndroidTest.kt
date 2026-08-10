package eu.kanade.tachiyomi.util.system

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [setUserAgent] against a real WebView, since the client-hint metadata it writes only
 * exists on-device and is what Chromium turns into the `Sec-CH-UA` request headers.
 */
@RunWith(AndroidJUnit4::class)
class WebViewUserAgentAndroidTest {

    @Before
    fun requireClientHintSupport() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA))
    }

    @Test
    fun chromeUserAgent_rewritesBrandsAndVersionsToMatch() {
        val metadata = metadataAfterSettingUserAgent(CHROME_USER_AGENT)

        val brands = metadata.brandVersionList.map { it.brand }
        assertTrue(
            "Expected the real WebView brand to be replaced, got $brands",
            WEBVIEW_BRAND !in brands,
        )
        assertTrue("Expected a Google Chrome brand, got $brands", CHROME_BRAND in brands)

        metadata.brandVersionList
            .filter { it.brand == CHROME_BRAND || it.brand == CHROMIUM_BRAND }
            .forEach {
                assertEquals("149", it.majorVersion)
                assertEquals("149.0.0.0", it.fullVersion)
            }
        assertEquals("149.0.0.0", metadata.fullVersion)
    }

    @Test
    fun chromeUserAgent_keepsGreasingBrandUntouched() {
        val before = metadataOfFreshWebView()
        val greasedBefore = before.brandVersionList
            .filter { it.brand != WEBVIEW_BRAND && it.brand != CHROMIUM_BRAND }
        assumeTrue(greasedBefore.isNotEmpty())

        val after = metadataAfterSettingUserAgent(CHROME_USER_AGENT)
        val greasedAfter = after.brandVersionList
            .filter { it.brand != WEBVIEW_BRAND && it.brand != CHROME_BRAND && it.brand != CHROMIUM_BRAND }

        assertEquals(
            greasedBefore.map { it.brand to it.majorVersion },
            greasedAfter.map { it.brand to it.majorVersion },
        )
    }

    @Test
    fun nonChromeUserAgent_leavesClientHintsUntouched() {
        val untouched = metadataOfFreshWebView()
        val afterFirefox = metadataAfterSettingUserAgent(FIREFOX_USER_AGENT)

        assertEquals(
            untouched.brandVersionList.map { it.brand to it.fullVersion },
            afterFirefox.brandVersionList.map { it.brand to it.fullVersion },
        )
        assertEquals(untouched.fullVersion, afterFirefox.fullVersion)
    }

    @Test
    fun userAgentString_isAlwaysApplied() {
        onWebView { webView ->
            webView.setUserAgent(FIREFOX_USER_AGENT)
            assertEquals(FIREFOX_USER_AGENT, webView.settings.userAgentString)

            webView.setUserAgent(CHROME_USER_AGENT)
            assertEquals(CHROME_USER_AGENT, webView.settings.userAgentString)
        }
    }

    private fun metadataOfFreshWebView(): UserAgentMetadata {
        return onWebView { WebSettingsCompat.getUserAgentMetadata(it.settings) }
    }

    private fun metadataAfterSettingUserAgent(userAgent: String): UserAgentMetadata {
        return onWebView { webView ->
            webView.setUserAgent(userAgent)
            WebSettingsCompat.getUserAgentMetadata(webView.settings)
        }
    }

    private fun <T> onWebView(block: (WebView) -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = WebView(ApplicationProvider.getApplicationContext())
            result = runCatching { block(webView) }
            webView.destroy()
        }
        return result!!.getOrThrow()
    }

    private companion object {
        const val WEBVIEW_BRAND = "Android WebView"
        const val CHROMIUM_BRAND = "Chromium"
        const val CHROME_BRAND = "Google Chrome"

        const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/149.0.0.0 Mobile Safari/537.36"
        const val FIREFOX_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0"
    }
}
