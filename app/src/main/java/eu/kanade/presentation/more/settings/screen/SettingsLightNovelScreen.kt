package eu.kanade.presentation.more.settings.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginManager
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsLightNovelScreen : SearchableSettings {
    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_category_light_novels

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val preferences = remember { Injekt.get<NovelFeaturePreferences>() }
        val pluginManager = remember { Injekt.get<LightNovelPluginManager>() }

        val enableLightNovelsPref = remember { preferences.enableLightNovels() }
        val lightNovelPluginChannelPref = remember { preferences.lightNovelPluginChannel() }

        val enabled by enableLightNovelsPref.collectAsState()
        val channel by lightNovelPluginChannelPref.collectAsState()

        var status by remember {
            mutableStateOf(
                LightNovelPluginManager.PluginStatus(
                    installed = false,
                    signedAndTrusted = false,
                    compatible = false,
                    installedVersionCode = null,
                ),
            )
        }
        var installInProgress by remember { mutableStateOf(false) }
        var installError: String? by remember { mutableStateOf(null) }
        var pendingEnableAfterInstall: Boolean? by remember { mutableStateOf(null) }
        var enableAfterInstallAwaitingPackageAdded by remember { mutableStateOf(false) }

        fun refreshStatus() {
            status = pluginManager.getPluginStatus()
        }

        LaunchedEffect(enabled, channel) {
            refreshStatus()
        }

        DisposableEffect(context) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val packageName = intent?.data?.schemeSpecificPart ?: return
                    if (packageName == LightNovelPluginManager.PLUGIN_PACKAGE_NAME) {
                        refreshStatus()
                        val wasAdded = intent.action == Intent.ACTION_PACKAGE_ADDED
                        if (wasAdded && enableAfterInstallAwaitingPackageAdded && pluginManager.isPluginReady()) {
                            enableAfterInstallAwaitingPackageAdded = false
                            scope.launch { enableLightNovelsPref.set(true) }
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            onDispose {
                context.unregisterReceiver(receiver)
            }
        }

        fun installErrorMessage(code: LightNovelPluginManager.InstallErrorCode) = when (code) {
            LightNovelPluginManager.InstallErrorCode.INSTALL_DISABLED ->
                AYMR.strings.light_novel_plugin_error_install_disabled
            LightNovelPluginManager.InstallErrorCode.MANIFEST_FETCH_FAILED ->
                AYMR.strings.light_novel_plugin_error_manifest_fetch_failed
            LightNovelPluginManager.InstallErrorCode.MANIFEST_PACKAGE_MISMATCH ->
                AYMR.strings.light_novel_plugin_error_manifest_package_mismatch
            LightNovelPluginManager.InstallErrorCode.MANIFEST_API_MISMATCH ->
                AYMR.strings.light_novel_plugin_error_manifest_api_mismatch
            LightNovelPluginManager.InstallErrorCode.MANIFEST_HOST_TOO_OLD ->
                AYMR.strings.light_novel_plugin_error_manifest_host_too_old
            LightNovelPluginManager.InstallErrorCode.MANIFEST_HOST_TOO_NEW ->
                AYMR.strings.light_novel_plugin_error_manifest_host_too_new
            LightNovelPluginManager.InstallErrorCode.DOWNLOAD_FAILED ->
                AYMR.strings.light_novel_plugin_error_download_failed
            LightNovelPluginManager.InstallErrorCode.INVALID_PLUGIN_APK ->
                AYMR.strings.light_novel_plugin_error_invalid_apk
            LightNovelPluginManager.InstallErrorCode.ARCHIVE_PACKAGE_MISMATCH ->
                AYMR.strings.light_novel_plugin_error_archive_package_mismatch
            LightNovelPluginManager.InstallErrorCode.INSTALL_LAUNCH_FAILED ->
                AYMR.strings.light_novel_plugin_error_install_launch_failed
            LightNovelPluginManager.InstallErrorCode.MANIFEST_PLUGIN_TOO_OLD ->
                AYMR.strings.light_novel_plugin_error_manifest_plugin_too_old
            LightNovelPluginManager.InstallErrorCode.MANIFEST_WRONG_CHANNEL ->
                AYMR.strings.light_novel_plugin_error_manifest_wrong_channel
            LightNovelPluginManager.InstallErrorCode.ROLLBACK_NOT_AVAILABLE ->
                AYMR.strings.light_novel_plugin_error_rollback_not_available
        }

        suspend fun runInstallFlow(enableFeatureAfterInstall: Boolean) {
            installInProgress = true
            installError = null

            try {
                when (val result = pluginManager.ensurePluginReady(channel)) {
                    is LightNovelPluginManager.InstallResult.AlreadyReady -> {
                        if (enableFeatureAfterInstall) {
                            enableLightNovelsPref.set(true)
                        }
                        context.toast(AYMR.strings.light_novel_plugin_status_ready_short)
                    }
                    is LightNovelPluginManager.InstallResult.InstallLaunched -> {
                        if (enableFeatureAfterInstall) {
                            // System package installer is asynchronous; keep toggle off until package
                            // add + readiness is confirmed via receiver.
                            enableAfterInstallAwaitingPackageAdded = true
                        }
                        context.toast(AYMR.strings.light_novel_plugin_install_started)
                    }
                    is LightNovelPluginManager.InstallResult.Error -> {
                        installError = context.stringResource(installErrorMessage(result.code))
                        context.toast(installErrorMessage(result.code))
                        if (enableFeatureAfterInstall) {
                            enableAfterInstallAwaitingPackageAdded = false
                            enableLightNovelsPref.set(false)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                installError = e.message ?: "Install failed"
                if (enableFeatureAfterInstall) {
                    enableAfterInstallAwaitingPackageAdded = false
                    enableLightNovelsPref.set(false)
                }
            } finally {
                refreshStatus()
                installInProgress = false
            }
        }

        if (pendingEnableAfterInstall != null) {
            LightNovelInstallConfirmDialog(
                title = stringResource(AYMR.strings.light_novel_plugin_install_confirm_title),
                message = stringResource(AYMR.strings.light_novel_plugin_install_confirm_message),
                onConfirm = {
                    val enableAfterInstall = pendingEnableAfterInstall ?: return@LightNovelInstallConfirmDialog
                    pendingEnableAfterInstall = null
                    scope.launch { runInstallFlow(enableAfterInstall) }
                },
                onDismiss = { pendingEnableAfterInstall = null },
            )
        }

        val statusText = when {
            installInProgress -> stringResource(AYMR.strings.light_novel_plugin_status_installing)
            installError != null -> stringResource(AYMR.strings.light_novel_plugin_status_error, installError ?: "")
            !status.installed -> stringResource(AYMR.strings.light_novel_plugin_status_missing)
            !status.signedAndTrusted -> stringResource(AYMR.strings.light_novel_plugin_status_untrusted)
            !status.compatible -> stringResource(AYMR.strings.light_novel_plugin_status_incompatible)
            else -> {
                val version = status.installedVersionCode ?: 0L
                stringResource(AYMR.strings.light_novel_plugin_status_ready, version)
            }
        }

        return listOf(
            Preference.PreferenceGroup(
                title = stringResource(AYMR.strings.pref_category_light_novels),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = enableLightNovelsPref,
                        title = stringResource(AYMR.strings.pref_enable_light_novels),
                        subtitle = stringResource(AYMR.strings.pref_enable_light_novels_summary),
                        onValueChanged = { newValue ->
                            if (installInProgress) {
                                return@SwitchPreference false
                            }

                            installError = null

                            if (newValue) {
                                if (pluginManager.isPluginReady()) {
                                    return@SwitchPreference true
                                }
                                pendingEnableAfterInstall = true
                                return@SwitchPreference false
                            }
                            true
                        },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = lightNovelPluginChannelPref,
                        entries = mapOf(
                            NovelFeaturePreferences.CHANNEL_STABLE to
                                stringResource(AYMR.strings.pref_light_novel_plugin_channel_stable),
                            NovelFeaturePreferences.CHANNEL_BETA to
                                stringResource(AYMR.strings.pref_light_novel_plugin_channel_beta),
                        ).toImmutableMap(),
                        title = stringResource(AYMR.strings.pref_light_novel_plugin_channel),
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_light_novel_plugin_status),
                        subtitle = statusText,
                        enabled = false,
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_light_novel_plugin_install_update),
                        subtitle = stringResource(AYMR.strings.pref_light_novel_plugin_install_update_summary),
                        enabled = enabled && !installInProgress,
                        onClick = {
                            installError = null
                            pendingEnableAfterInstall = false
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_light_novel_plugin_uninstall),
                        enabled = status.installed && !installInProgress,
                        onClick = {
                            pluginManager.uninstallPlugin()
                        },
                    ),
                ),
            ),
        )
    }
}

@Composable
private fun LightNovelInstallConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(MR.strings.action_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@PreviewLightDark
@Composable
private fun LightNovelInstallConfirmDialogPreview() {
    LightNovelInstallConfirmDialog(
        title = "Install light novel plugin?",
        message =
        "This downloads the plugin manifest and APK from GitHub before launching Android package install.",
        onConfirm = {},
        onDismiss = {},
    )
}
