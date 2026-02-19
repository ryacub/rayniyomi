package eu.kanade.presentation.more

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.GetApp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.VideoSettings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.feature.novel.IncompatibleReason
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginUiState
import eu.kanade.tachiyomi.ui.more.DownloadQueueState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MoreScreen(
    downloadQueueStateProvider: () -> DownloadQueueState,
    downloadedOnly: Boolean,
    onDownloadedOnlyChange: (Boolean) -> Unit,
    incognitoMode: Boolean,
    onIncognitoModeChange: (Boolean) -> Unit,
    navStyle: NavStyle,
    onClickAlt: () -> Unit,
    onClickDownloadQueue: () -> Unit,
    onClickCategories: () -> Unit,
    onClickStats: () -> Unit,
    onClickStorage: () -> Unit,
    onClickDataAndStorage: () -> Unit,
    onClickPlayerSettings: () -> Unit,
    onClickSettings: () -> Unit,
    onClickAbout: () -> Unit,
    lightNovelUiState: LightNovelPluginUiState = LightNovelPluginUiState.Disabled,
    onClickLightNovels: () -> Unit = {},
    onClickInstallPlugin: () -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current

    Scaffold { contentPadding ->
        ScrollbarLazyColumn(
            modifier = Modifier.padding(contentPadding),
        ) {
            item {
                LogoHeader()
            }
            item {
                SwitchPreferenceWidget(
                    title = stringResource(MR.strings.label_downloaded_only),
                    subtitle = stringResource(MR.strings.downloaded_only_summary),
                    icon = Icons.Outlined.CloudOff,
                    checked = downloadedOnly,
                    onCheckedChanged = onDownloadedOnlyChange,
                )
            }
            item {
                SwitchPreferenceWidget(
                    title = stringResource(MR.strings.pref_incognito_mode),
                    subtitle = stringResource(AYMR.strings.pref_incognito_mode_summary),
                    icon = ImageVector.vectorResource(R.drawable.ic_glasses_24dp),
                    checked = incognitoMode,
                    onCheckedChanged = onIncognitoModeChange,
                )
            }

            item { HorizontalDivider() }

            item {
                TextPreferenceWidget(
                    title = navStyle.moreTab.options.title,
                    icon = navStyle.moreIcon,
                    onPreferenceClick = onClickAlt,
                )
            }

            when (lightNovelUiState) {
                LightNovelPluginUiState.Disabled -> Unit
                LightNovelPluginUiState.Ready -> item {
                    TextPreferenceWidget(
                        title = stringResource(AYMR.strings.pref_category_light_novels),
                        icon = Icons.Outlined.Book,
                        onPreferenceClick = onClickLightNovels,
                    )
                }
                LightNovelPluginUiState.Missing -> item {
                    TextPreferenceWidget(
                        title = stringResource(AYMR.strings.pref_category_light_novels),
                        subtitle = stringResource(AYMR.strings.light_novel_plugin_action_install),
                        icon = Icons.Outlined.Book,
                        onPreferenceClick = onClickInstallPlugin,
                    )
                }
                LightNovelPluginUiState.Downloading -> item {
                    TextPreferenceWidget(
                        title = stringResource(AYMR.strings.pref_category_light_novels),
                        subtitle = stringResource(AYMR.strings.light_novel_plugin_status_downloading),
                        icon = Icons.Outlined.Book,
                        onPreferenceClick = {},
                    )
                }
                LightNovelPluginUiState.Installing -> item {
                    TextPreferenceWidget(
                        title = stringResource(AYMR.strings.pref_category_light_novels),
                        subtitle = stringResource(AYMR.strings.light_novel_plugin_status_waiting_for_install),
                        icon = Icons.Outlined.Book,
                        onPreferenceClick = {},
                    )
                }
                is LightNovelPluginUiState.Incompatible -> item {
                    val subtitle = when (lightNovelUiState.reason) {
                        IncompatibleReason.UNTRUSTED ->
                            stringResource(AYMR.strings.light_novel_plugin_status_incompatible)
                        IncompatibleReason.API_MISMATCH ->
                            stringResource(AYMR.strings.light_novel_plugin_status_api_mismatch)
                    }
                    TextPreferenceWidget(
                        title = stringResource(AYMR.strings.pref_category_light_novels),
                        subtitle = subtitle,
                        icon = Icons.Outlined.Book,
                        onPreferenceClick = onClickInstallPlugin,
                    )
                }
                is LightNovelPluginUiState.Blocked -> item {
                    TextPreferenceWidget(
                        title = stringResource(AYMR.strings.pref_category_light_novels),
                        subtitle = lightNovelUiState.reason.ifEmpty {
                            stringResource(AYMR.strings.light_novel_plugin_status_blocked)
                        },
                        icon = Icons.Outlined.Book,
                        onPreferenceClick = {},
                    )
                }
            }

            item {
                val downloadQueueState = downloadQueueStateProvider()
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_download_queue),
                    subtitle = when (downloadQueueState) {
                        DownloadQueueState.Stopped -> null
                        is DownloadQueueState.Paused -> {
                            val pending = downloadQueueState.pending
                            if (pending == 0) {
                                stringResource(MR.strings.paused)
                            } else {
                                "${stringResource(MR.strings.paused)} • ${
                                    pluralStringResource(
                                        MR.plurals.download_queue_summary,
                                        count = pending,
                                        pending,
                                    )
                                }"
                            }
                        }

                        is DownloadQueueState.Downloading -> {
                            val pending = downloadQueueState.pending
                            pluralStringResource(
                                MR.plurals.download_queue_summary,
                                count = pending,
                                pending,
                            )
                        }
                    },
                    icon = Icons.Outlined.GetApp,
                    onPreferenceClick = onClickDownloadQueue,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(AYMR.strings.general_categories),
                    icon = Icons.AutoMirrored.Outlined.Label,
                    onPreferenceClick = onClickCategories,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_stats),
                    icon = Icons.Outlined.QueryStats,
                    onPreferenceClick = onClickStats,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_data_storage),
                    icon = Icons.Outlined.Storage,
                    onPreferenceClick = onClickDataAndStorage,
                )
            }

            item { HorizontalDivider() }

            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_settings),
                    icon = Icons.Outlined.Settings,
                    onPreferenceClick = onClickSettings,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(AYMR.strings.label_player_settings),
                    icon = Icons.Outlined.VideoSettings,
                    onPreferenceClick = onClickPlayerSettings,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.pref_category_about),
                    icon = Icons.Outlined.Info,
                    onPreferenceClick = onClickAbout,
                )
            }
            item {
                TextPreferenceWidget(
                    title = stringResource(MR.strings.label_help),
                    icon = Icons.AutoMirrored.Outlined.HelpOutline,
                    onPreferenceClick = { uriHandler.openUri(Constants.URL_HELP) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun MoreScreenPreviewWithLightNovels() {
    MoreScreen(
        downloadQueueStateProvider = { DownloadQueueState.Stopped },
        downloadedOnly = false,
        onDownloadedOnlyChange = {},
        incognitoMode = false,
        onIncognitoModeChange = {},
        navStyle = NavStyle.MOVE_MANGA_TO_MORE,
        onClickAlt = {},
        onClickDownloadQueue = {},
        onClickCategories = {},
        onClickStats = {},
        onClickStorage = {},
        onClickDataAndStorage = {},
        onClickPlayerSettings = {},
        onClickSettings = {},
        onClickAbout = {},
        lightNovelUiState = LightNovelPluginUiState.Ready,
        onClickLightNovels = {},
        onClickInstallPlugin = {},
    )
}

@Preview
@Composable
private fun MoreScreenPreviewWithoutLightNovels() {
    MoreScreen(
        downloadQueueStateProvider = { DownloadQueueState.Stopped },
        downloadedOnly = false,
        onDownloadedOnlyChange = {},
        incognitoMode = false,
        onIncognitoModeChange = {},
        navStyle = NavStyle.MOVE_MANGA_TO_MORE,
        onClickAlt = {},
        onClickDownloadQueue = {},
        onClickCategories = {},
        onClickStats = {},
        onClickStorage = {},
        onClickDataAndStorage = {},
        onClickPlayerSettings = {},
        onClickSettings = {},
        onClickAbout = {},
        lightNovelUiState = LightNovelPluginUiState.Disabled,
        onClickLightNovels = {},
        onClickInstallPlugin = {},
    )
}

@Preview
@Composable
private fun MoreScreenPreviewMissingPlugin() {
    MoreScreen(
        downloadQueueStateProvider = { DownloadQueueState.Stopped },
        downloadedOnly = false,
        onDownloadedOnlyChange = {},
        incognitoMode = false,
        onIncognitoModeChange = {},
        navStyle = NavStyle.MOVE_MANGA_TO_MORE,
        onClickAlt = {},
        onClickDownloadQueue = {},
        onClickCategories = {},
        onClickStats = {},
        onClickStorage = {},
        onClickDataAndStorage = {},
        onClickPlayerSettings = {},
        onClickSettings = {},
        onClickAbout = {},
        lightNovelUiState = LightNovelPluginUiState.Missing,
        onClickLightNovels = {},
        onClickInstallPlugin = {},
    )
}

@Preview
@Composable
private fun MoreScreenPreviewDownloadingPlugin() {
    MoreScreen(
        downloadQueueStateProvider = { DownloadQueueState.Stopped },
        downloadedOnly = false,
        onDownloadedOnlyChange = {},
        incognitoMode = false,
        onIncognitoModeChange = {},
        navStyle = NavStyle.MOVE_MANGA_TO_MORE,
        onClickAlt = {},
        onClickDownloadQueue = {},
        onClickCategories = {},
        onClickStats = {},
        onClickStorage = {},
        onClickDataAndStorage = {},
        onClickPlayerSettings = {},
        onClickSettings = {},
        onClickAbout = {},
        lightNovelUiState = LightNovelPluginUiState.Downloading,
        onClickLightNovels = {},
        onClickInstallPlugin = {},
    )
}

@Preview
@Composable
private fun MoreScreenPreviewInstallingPlugin() {
    MoreScreen(
        downloadQueueStateProvider = { DownloadQueueState.Stopped },
        downloadedOnly = false,
        onDownloadedOnlyChange = {},
        incognitoMode = false,
        onIncognitoModeChange = {},
        navStyle = NavStyle.MOVE_MANGA_TO_MORE,
        onClickAlt = {},
        onClickDownloadQueue = {},
        onClickCategories = {},
        onClickStats = {},
        onClickStorage = {},
        onClickDataAndStorage = {},
        onClickPlayerSettings = {},
        onClickSettings = {},
        onClickAbout = {},
        lightNovelUiState = LightNovelPluginUiState.Installing,
        onClickLightNovels = {},
        onClickInstallPlugin = {},
    )
}

@Preview
@Composable
private fun MoreScreenPreviewIncompatiblePlugin() {
    MoreScreen(
        downloadQueueStateProvider = { DownloadQueueState.Stopped },
        downloadedOnly = false,
        onDownloadedOnlyChange = {},
        incognitoMode = false,
        onIncognitoModeChange = {},
        navStyle = NavStyle.MOVE_MANGA_TO_MORE,
        onClickAlt = {},
        onClickDownloadQueue = {},
        onClickCategories = {},
        onClickStats = {},
        onClickStorage = {},
        onClickDataAndStorage = {},
        onClickPlayerSettings = {},
        onClickSettings = {},
        onClickAbout = {},
        lightNovelUiState = LightNovelPluginUiState.Incompatible(IncompatibleReason.API_MISMATCH),
        onClickLightNovels = {},
        onClickInstallPlugin = {},
    )
}

@Preview
@Composable
private fun MoreScreenPreviewBlockedPlugin() {
    MoreScreen(
        downloadQueueStateProvider = { DownloadQueueState.Stopped },
        downloadedOnly = false,
        onDownloadedOnlyChange = {},
        incognitoMode = false,
        onIncognitoModeChange = {},
        navStyle = NavStyle.MOVE_MANGA_TO_MORE,
        onClickAlt = {},
        onClickDownloadQueue = {},
        onClickCategories = {},
        onClickStats = {},
        onClickStorage = {},
        onClickDataAndStorage = {},
        onClickPlayerSettings = {},
        onClickSettings = {},
        onClickAbout = {},
        lightNovelUiState = LightNovelPluginUiState.Blocked(""),
        onClickLightNovels = {},
        onClickInstallPlugin = {},
    )
}
