package eu.kanade.tachiyomi.util.system

import eu.kanade.tachiyomi.network.NetworkPreferences
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class UserAgentMetadataTest {

    /**
     * Guards the coupling the whole client-hint path depends on: if the shipped default stops being
     * Chrome-shaped, [WebView.setUserAgent] silently stops spoofing `Sec-CH-UA`.
     */
    @Test
    fun `shipped default user agent is Chrome-shaped so client hints are spoofed`() {
        val default = NetworkPreferences(InMemoryPreferenceStore()).defaultUserAgent().defaultValue()

        parseChromeUserAgentVersion(default) shouldNotBe null
    }

    @Test
    fun `keeps the full version when the user agent carries all four components`() {
        val version = parseChromeUserAgentVersion(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/149.0.7632.104 Mobile Safari/537.36",
        )

        version shouldBe ChromeUserAgentVersion(major = "149", full = "149.0.7632.104")
    }

    @Test
    fun `pads a bare major version so the full version stays four components`() {
        val version = parseChromeUserAgentVersion("Chrome/149 Mobile Safari/537.36")

        version shouldBe ChromeUserAgentVersion(major = "149", full = "149.0.0.0")
    }

    @Test
    fun `pads a two component version so the full version stays four components`() {
        val version = parseChromeUserAgentVersion("Chrome/149.0 Mobile Safari/537.36")

        version shouldBe ChromeUserAgentVersion(major = "149", full = "149.0.0.0")
    }

    @Test
    fun `pads a three component version so the full version stays four components`() {
        val version = parseChromeUserAgentVersion("Chrome/149.0.7632 Mobile Safari/537.36")

        version shouldBe ChromeUserAgentVersion(major = "149", full = "149.0.7632.0")
    }

    @Test
    fun `pads a trailing dot version so the full version stays four components`() {
        val version = parseChromeUserAgentVersion("Chrome/149. Mobile Safari/537.36")

        version shouldBe ChromeUserAgentVersion(major = "149", full = "149.0.0.0")
    }

    @Test
    fun `returns null for a Firefox user agent so client hints are left untouched`() {
        val version = parseChromeUserAgentVersion(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0",
        )

        version shouldBe null
    }

    @Test
    fun `returns null for a Safari user agent so client hints are left untouched`() {
        val version = parseChromeUserAgentVersion(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1",
        )

        version shouldBe null
    }

    @Test
    fun `returns null when the Chrome token has no version`() {
        val version = parseChromeUserAgentVersion("Mozilla/5.0 Chrome Mobile Safari/537.36")

        version shouldBe null
    }

    @Test
    fun `maps the real WebView brand onto Google Chrome`() {
        spoofedBrand("Android WebView") shouldBe "Google Chrome"
    }

    @Test
    fun `keeps the Chromium brand as Chromium`() {
        spoofedBrand("Chromium") shouldBe "Chromium"
    }

    @Test
    fun `leaves the greasing brand untouched`() {
        spoofedBrand("Not?A_Brand") shouldBe null
    }

    @Test
    fun `leaves an unrecognized brand untouched`() {
        spoofedBrand("Microsoft Edge") shouldBe null
    }
}
