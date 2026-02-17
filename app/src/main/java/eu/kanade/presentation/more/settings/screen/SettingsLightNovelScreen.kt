package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
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

        val enabled by preferences.enableLightNovels().collectAsState()
        val channel by preferences.lightNovelPluginChannel().collectAsState()

        var status by remember(enabled, channel) { mutableStateOf(pluginManager.getPluginStatus()) }

        fun refreshStatus() {
            status = pluginManager.getPluginStatus()
        }

        val statusText = when {
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
                        preference = preferences.enableLightNovels(),
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
                                        is LightNovelPluginManager.InstallResult.Error -> {
                                            preferences.enableLightNovels().set(false)
                                            context.toast(result.message)
                                        }
                                    }
                                    refreshStatus()
                                }
                            }
                            true
                        },
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = preferences.lightNovelPluginChannel(),
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
                                    is LightNovelPluginManager.InstallResult.Error -> {
                                        context.toast(result.message)
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
                            refreshStatus()
                        },
                    ),
                ),
            ),
        )
    }
}
