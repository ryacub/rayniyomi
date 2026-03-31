package eu.kanade.tachiyomi.ui.browse.anime.extension

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.anime.AnimeExtensionScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.settings.screen.browse.AnimeExtensionReposScreen
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeExtensionDetailsScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.util.system.isPackageInstalled
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun animeExtensionsTab(
    extensionsScreenModel: AnimeExtensionsScreenModel,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current

    val state by extensionsScreenModel.state.collectAsStateWithLifecycle()
    var privateExtensionToUninstall by remember { mutableStateOf<AnimeExtension?>(null) }
    var invalidExtensionToUninstall by remember { mutableStateOf<AnimeLoadResult.Invalid?>(null) }

    return TabContent(
        titleRes = AYMR.strings.label_anime_extensions,
        badgeNumber = state.updates.takeIf { it > 0 },
        searchEnabled = true,
        actions = persistentListOf(
            AppBar.OverflowAction(
                title = stringResource(MR.strings.action_filter),
                onClick = {
                    navigator.push(
                        AnimeExtensionFilterScreen(),
                    )
                },
            ),
            AppBar.OverflowAction(
                title = stringResource(MR.strings.label_extension_repos),
                onClick = { navigator.push(AnimeExtensionReposScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            val noNetworkString = stringResource(MR.strings.exception_offline)
            LaunchedEffect(Unit) {
                extensionsScreenModel.events.collectLatest { event ->
                    when (event) {
                        AnimeExtensionsScreenModel.Event.DeviceOffline -> {
                            snackbarHostState.showSnackbar(noNetworkString)
                        }
                        is AnimeExtensionsScreenModel.Event.InvalidExtensionRevoked -> {
                            invalidExtensionToUninstall = event.extension
                        }
                    }
                }
            }
            AnimeExtensionScreen(
                state = state,
                contentPadding = contentPadding,
                searchQuery = state.searchQuery,
                onLongClickItem = { extension ->
                    when (extension) {
                        is AnimeExtension.Available -> extensionsScreenModel.installExtension(
                            extension,
                        )
                        else -> {
                            if (context.isPackageInstalled(extension.pkgName)) {
                                extensionsScreenModel.uninstallExtension(extension)
                            } else {
                                privateExtensionToUninstall = extension
                            }
                        }
                    }
                },
                onClickItemCancel = extensionsScreenModel::cancelInstallUpdateExtension,
                onClickUpdateAll = extensionsScreenModel::updateAllExtensions,
                onOpenWebView = { extension ->
                    extension.sources.getOrNull(0)?.let {
                        navigator.push(
                            WebViewScreen(
                                url = it.baseUrl,
                                initialTitle = it.name,
                                sourceId = it.id,
                            ),
                        )
                    }
                },
                onInstallExtension = extensionsScreenModel::installExtension,
                onOpenExtension = { navigator.push(AnimeExtensionDetailsScreen(it.pkgName)) },
                onTrustExtension = { extensionsScreenModel.trustExtension(it) },
                onUninstallExtension = { extensionsScreenModel.uninstallExtension(it) },
                onUpdateExtension = extensionsScreenModel::updateExtension,
                onRefresh = extensionsScreenModel::findAvailableExtensions,
                onRetryProbe = extensionsScreenModel::retryProbe,
            )

            privateExtensionToUninstall?.let { extension ->
                AnimeExtensionUninstallConfirmation(
                    extensionName = extension.name,
                    onClickConfirm = {
                        extensionsScreenModel.uninstallExtension(extension)
                    },
                    onDismissRequest = {
                        privateExtensionToUninstall = null
                    },
                )
            }

            invalidExtensionToUninstall?.let { invalidExtension ->
                AnimeInvalidExtensionConfirmation(
                    extensionName = invalidExtension.name,
                    onClickConfirm = {
                        extensionsScreenModel.uninstallExtension(invalidExtension.pkgName)
                    },
                    onDismissRequest = {
                        invalidExtensionToUninstall = null
                    },
                )
            }
        },
    )
}

@Composable
private fun AnimeExtensionUninstallConfirmation(
    extensionName: String,
    onClickConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(MR.strings.ext_confirm_remove))
        },
        text = {
            Text(text = stringResource(MR.strings.remove_private_extension_message, extensionName))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onClickConfirm()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.ext_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun AnimeInvalidExtensionConfirmation(
    extensionName: String,
    onClickConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = stringResource(MR.strings.ext_invalid_extension_title))
        },
        text = {
            Text(text = stringResource(MR.strings.ext_invalid_extension_message, extensionName))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onClickConfirm()
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.ext_uninstall))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        onDismissRequest = onDismissRequest,
    )
}
