package eu.kanade.presentation.more

import eu.kanade.tachiyomi.feature.novel.IncompatibleReason
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginCompatibilityCategory
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginDiagnostics
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginUiState
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.i18n.aniyomi.AYMR

class LightNovelPluginDiagnosticMessagesTest {

    @Test
    fun `untrusted diagnostic selects reinstall resource and ordered arguments`() {
        val message = lightNovelPluginDiagnosticMessage(
            incompatible(
                reason = IncompatibleReason.UNTRUSTED,
                compatibility = LightNovelPluginCompatibilityCategory.HOST_TOO_OLD,
            ),
        )

        message.resource shouldBe AYMR.strings.light_novel_plugin_status_untrusted_diagnostic
        message.arguments shouldBe listOf(
            "xyz.rayniyomi.plugin.lightnovel",
            42L,
            "host too old",
        )
    }

    @Test
    fun `api mismatch diagnostic selects update resource and uses unknown API fallback`() {
        val message = lightNovelPluginDiagnosticMessage(
            incompatible(
                reason = IncompatibleReason.API_MISMATCH,
                compatibility = LightNovelPluginCompatibilityCategory.HOST_TOO_NEW,
                pluginApiVersion = null,
            ),
        )

        message.resource shouldBe AYMR.strings.light_novel_plugin_status_api_mismatch_diagnostic
        message.arguments shouldBe listOf(
            "xyz.rayniyomi.plugin.lightnovel",
            42L,
            "host too new",
            "unknown",
            2,
            343L,
        )
    }

    private fun incompatible(
        reason: IncompatibleReason,
        compatibility: LightNovelPluginCompatibilityCategory,
        pluginApiVersion: Int? = 1,
    ) = LightNovelPluginUiState.Incompatible(
        reason = reason,
        diagnostics = LightNovelPluginDiagnostics(
            packageName = "xyz.rayniyomi.plugin.lightnovel",
            installedVersionCode = 42L,
            signedAndTrusted = reason != IncompatibleReason.UNTRUSTED,
            compatibility = compatibility,
            pluginApiVersion = pluginApiVersion,
            expectedPluginApiVersion = 2,
            hostVersionCode = 343L,
        ),
    )
}
