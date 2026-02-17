package eu.kanade.presentation.more.settings.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginManager
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
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

        var status by remember(enabled, channel) { mutableStateOf(pluginManager.getPluginStatus()) }

        suspend fun refreshStatus() {
            status = pluginManager.getPluginStatus(channel)
        }

        LaunchedEffect(channel) {
            refreshStatus()
        }

        DisposableEffect(context) {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val packageName = intent?.data?.schemeSpecificPart ?: return
                    if (packageName == LightNovelPluginManager.PLUGIN_PACKAGE_NAME) {
                        scope.launch {
                            refreshStatus()
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

        val statusText = when {
            !status.installed -> stringResource(AYMR.strings.light_novel_plugin_status_missing)
            !status.signedAndTrusted -> stringResource(AYMR.strings.light_novel_plugin_status_untrusted)
            !status.compatible -> when (status.compatibilityState) {
                LightNovelPluginManager.CompatibilityState.API_MISMATCH,
                LightNovelPluginManager.CompatibilityState.MISSING_METADATA,
                -> stringResource(AYMR.strings.light_novel_plugin_status_api_mismatch)
                LightNovelPluginManager.CompatibilityState.HOST_TOO_OLD ->
                    stringResource(AYMR.strings.light_novel_plugin_status_host_too_old)
                LightNovelPluginManager.CompatibilityState.HOST_TOO_NEW ->
                    stringResource(AYMR.strings.light_novel_plugin_status_host_too_new)
                LightNovelPluginManager.CompatibilityState.READY ->
                    stringResource(AYMR.strings.light_novel_plugin_status_incompatible)
            }
            status.versionState == LightNovelPluginManager.VersionState.INSTALLED_OUTDATED -> {
                val availableVersion = status.availableVersionCode ?: 0L
                stringResource(AYMR.strings.light_novel_plugin_status_outdated, availableVersion)
            }
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
                            if (newValue) {
                                scope.launch {
                                    val result = pluginManager.ensurePluginReady(channel)
                                    when (result) {
                                        is LightNovelPluginManager.InstallResult.AlreadyReady -> {
                                            context.toast(AYMR.strings.light_novel_plugin_status_ready_short)
                                        }
                                        is LightNovelPluginManager.InstallResult.InstallLaunched -> {
                                            context.toast(AYMR.strings.light_novel_plugin_install_started)
                                        }
                                        is LightNovelPluginManager.InstallResult.Rejected -> {
                                            enableLightNovelsPref.set(false)
                                            context.toast(result.reason.toMessageRes())
                                        }
                                    }
                                    refreshStatus()
                                }
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
                        enabled = enabled,
                        onClick = {
                            scope.launch {
                                val result = pluginManager.ensurePluginReady(channel)
                                when (result) {
                                    is LightNovelPluginManager.InstallResult.AlreadyReady -> {
                                        context.toast(AYMR.strings.light_novel_plugin_status_ready_short)
                                    }
                                    is LightNovelPluginManager.InstallResult.InstallLaunched -> {
                                        context.toast(AYMR.strings.light_novel_plugin_install_started)
                                    }
                                    is LightNovelPluginManager.InstallResult.Rejected -> {
                                        context.toast(result.reason.toMessageRes())
                                    }
                                }
                                refreshStatus()
                            }
                        },
                    ),
                    Preference.PreferenceItem.TextPreference(
                        title = stringResource(AYMR.strings.pref_light_novel_plugin_uninstall),
                        enabled = status.installed,
                        onClick = {
                            pluginManager.uninstallPlugin()
                            scope.launch { refreshStatus() }
                        },
                    ),
                ),
            ),
        )
    }
}

private fun LightNovelPluginManager.RejectionReason.toMessageRes() = when (this) {
    LightNovelPluginManager.RejectionReason.INSTALL_DISABLED ->
        AYMR.strings.light_novel_plugin_error_install_disabled
    LightNovelPluginManager.RejectionReason.MANIFEST_FETCH_FAILED ->
        AYMR.strings.light_novel_plugin_error_manifest_fetch_failed
    LightNovelPluginManager.RejectionReason.INVALID_MANIFEST ->
        AYMR.strings.light_novel_plugin_error_invalid_manifest
    LightNovelPluginManager.RejectionReason.DOWNLOAD_FAILED ->
        AYMR.strings.light_novel_plugin_error_download_failed
    LightNovelPluginManager.RejectionReason.CHECKSUM_MISMATCH ->
        AYMR.strings.light_novel_plugin_error_checksum_mismatch
    LightNovelPluginManager.RejectionReason.INVALID_PLUGIN_APK ->
        AYMR.strings.light_novel_plugin_error_invalid_apk
    LightNovelPluginManager.RejectionReason.UNSIGNED_PLUGIN_APK ->
        AYMR.strings.light_novel_plugin_error_unsigned_apk
    LightNovelPluginManager.RejectionReason.PACKAGE_NAME_MISMATCH ->
        AYMR.strings.light_novel_plugin_error_package_mismatch
    LightNovelPluginManager.RejectionReason.MISSING_SIGNER_PINS ->
        AYMR.strings.light_novel_plugin_error_missing_signer_pins
    LightNovelPluginManager.RejectionReason.SIGNER_MISMATCH ->
        AYMR.strings.light_novel_plugin_error_signer_mismatch
    LightNovelPluginManager.RejectionReason.PLUGIN_API_MISMATCH ->
        AYMR.strings.light_novel_plugin_error_api_mismatch
    LightNovelPluginManager.RejectionReason.HOST_VERSION_TOO_OLD ->
        AYMR.strings.light_novel_plugin_error_host_too_old
    LightNovelPluginManager.RejectionReason.HOST_VERSION_TOO_NEW ->
        AYMR.strings.light_novel_plugin_error_host_too_new
    LightNovelPluginManager.RejectionReason.INSTALL_LAUNCH_FAILED ->
        AYMR.strings.light_novel_plugin_error_install_launch_failed
}
