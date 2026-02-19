package eu.kanade.tachiyomi.feature.novel

import eu.kanade.domain.novel.NovelFeaturePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.core.common.util.system.logcat

/**
 * Singleton that owns and broadcasts [LightNovelPluginUiState].
 *
 * Both [eu.kanade.tachiyomi.ui.more.MoreTab] and [SettingsLightNovelScreen] observe
 * [uiState] to keep UI in sync with plugin lifecycle transitions.
 *
 * Call the lifecycle hooks ([onDownloadStarted], [onInstallLaunched], [onInstallIdle],
 * [refreshPluginStatus]) from the install flow to drive state transitions.
 */
class LightNovelPluginStateManager(
    private val pluginManager: LightNovelPluginManager,
    private val preferences: NovelFeaturePreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val installPhaseFlow = MutableStateFlow(InstallPhase.IDLE)
    private val pluginStatusFlow = MutableStateFlow(pluginManager.getPluginStatus())

    private val mutableUiState = MutableStateFlow<LightNovelPluginUiState>(LightNovelPluginUiState.Disabled)
    val uiState: StateFlow<LightNovelPluginUiState> = mutableUiState.asStateFlow()

    init {
        combine(
            preferences.enableLightNovels().changes(),
            pluginStatusFlow,
            installPhaseFlow,
        ) { enabled, status, phase ->
            resolvePluginUiState(
                featureEnabled = enabled,
                pluginStatus = status,
                installPhase = phase,
                installBlocked = !pluginManager.isPluginInstallEnabled(),
                blockReason = if (!pluginManager.isPluginInstallEnabled()) {
                    "Plugin install not enabled in this build"
                } else {
                    null
                },
            )
        }.onEach { newState ->
            logcat { "LightNovelPluginStateManager: state -> $newState" }
            mutableUiState.value = newState
        }.launchIn(scope)
    }

    fun onDownloadStarted() {
        installPhaseFlow.value = InstallPhase.DOWNLOADING
    }

    fun onInstallLaunched() {
        installPhaseFlow.value = InstallPhase.INSTALLING
    }

    fun onInstallIdle() {
        installPhaseFlow.value = InstallPhase.IDLE
    }

    fun refreshPluginStatus() {
        pluginStatusFlow.value = pluginManager.getPluginStatus()
    }
}
