package eu.kanade.presentation.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapVert
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.screens.EmptyScreenAction

enum class SourceErrorAction(val labelRes: StringResource) {
    Retry(MR.strings.action_retry),
    OpenInWebView(MR.strings.action_open_in_web_view),
    Help(MR.strings.label_help),
    Migrate(MR.strings.action_migrate),
}

fun SourceErrorAction.toEmptyScreenAction(onClick: () -> Unit): EmptyScreenAction {
    val icon = when (this) {
        SourceErrorAction.Retry -> Icons.Outlined.Refresh
        SourceErrorAction.OpenInWebView -> Icons.Outlined.Public
        SourceErrorAction.Help -> Icons.AutoMirrored.Outlined.HelpOutline
        SourceErrorAction.Migrate -> Icons.Outlined.SwapVert
    }
    return EmptyScreenAction(stringRes = labelRes, icon = icon, onClick = onClick)
}

fun browseSourceErrorActions(canMigrate: Boolean): List<SourceErrorAction> {
    return buildList {
        add(SourceErrorAction.Retry)
        add(SourceErrorAction.OpenInWebView)
        add(SourceErrorAction.Help)
        if (canMigrate) add(SourceErrorAction.Migrate)
    }
}

fun sourceHelpUrl(error: Throwable?): String {
    val isCloudflareBlock = generateSequence(error) { it.cause }
        .any { it is CloudflareBypassException }
    return if (isCloudflareBlock) Constants.URL_HELP_CLOUDFLARE else Constants.URL_HELP
}
