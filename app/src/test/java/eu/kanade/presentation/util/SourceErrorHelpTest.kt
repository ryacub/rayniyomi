package eu.kanade.presentation.util

import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.IOException

class SourceErrorHelpTest {

    @Test
    fun `help opens the cloudflare section for a failed cloudflare bypass`() {
        val error = IOException("Failed to bypass Cloudflare", CloudflareBypassException())

        sourceHelpUrl(error) shouldBe "${Constants.URL_HELP}#cloudflare"
    }

    @Test
    fun `help finds a cloudflare bypass failure under another wrapper`() {
        val error = IllegalStateException(IOException(CloudflareBypassException()))

        sourceHelpUrl(error) shouldBe "${Constants.URL_HELP}#cloudflare"
    }

    @Test
    fun `help opens the general page for other errors`() {
        sourceHelpUrl(IOException("HTTP error 500")) shouldBe Constants.URL_HELP
        sourceHelpUrl(null) shouldBe Constants.URL_HELP
    }

    @Test
    fun `browse error shows migrate only when the source has library entries`() {
        browseSourceErrorActions(canMigrate = true) shouldBe listOf(
            SourceErrorAction.Retry,
            SourceErrorAction.OpenInWebView,
            SourceErrorAction.Help,
            SourceErrorAction.Migrate,
        )
        browseSourceErrorActions(canMigrate = false) shouldBe listOf(
            SourceErrorAction.Retry,
            SourceErrorAction.OpenInWebView,
            SourceErrorAction.Help,
        )
    }
}
