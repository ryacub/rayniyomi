package eu.kanade.presentation.more

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.feature.novel.IncompatibleReason
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginUiState
import tachiyomi.i18n.aniyomi.AYMR

internal data class LightNovelPluginDiagnosticMessage(
    val resource: StringResource,
    val arguments: List<Any>,
)

internal fun lightNovelPluginDiagnosticMessage(
    state: LightNovelPluginUiState.Incompatible,
): LightNovelPluginDiagnosticMessage {
    val diagnostics = state.diagnostics
    val compatibility = diagnostics.compatibility.name
        .lowercase()
        .replace('_', ' ')
    return when (state.reason) {
        IncompatibleReason.UNTRUSTED -> LightNovelPluginDiagnosticMessage(
            resource = AYMR.strings.light_novel_plugin_status_untrusted_diagnostic,
            arguments = listOf(
                diagnostics.packageName,
                diagnostics.installedVersionCode,
                compatibility,
            ),
        )
        IncompatibleReason.API_MISMATCH -> LightNovelPluginDiagnosticMessage(
            resource = AYMR.strings.light_novel_plugin_status_api_mismatch_diagnostic,
            arguments = listOf(
                diagnostics.packageName,
                diagnostics.installedVersionCode,
                compatibility,
                diagnostics.pluginApiVersion?.toString() ?: "unknown",
                diagnostics.expectedPluginApiVersion,
                diagnostics.hostVersionCode,
            ),
        )
    }
}
