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
import kotlinx.coroutines.launch
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

    // Initialize with a safe default; the actual status is loaded asynchronously in init
    // to avoid calling PackageManager on the construction thread.
    private val pluginStatusFlow = MutableStateFlow(
        LightNovelPluginManager.PluginStatus(
            installed = false,
            signedAndTrusted = false,
            compatible = false,
            installedVersionCode = null,
        ),
    )

    private val mutableUiState = MutableStateFlow<LightNovelPluginUiState>(LightNovelPluginUiState.Disabled)
    val uiState: StateFlow<LightNovelPluginUiState> = mutableUiState.asStateFlow()

    init {
        // Load the real plugin status on the IO dispatcher to avoid blocking the caller thread.
        scope.launch {
            pluginStatusFlow.value = pluginManager.getPluginStatus()
        }

        combine(
            preferences.enableLightNovels().changes(),
            pluginStatusFlow,
            installPhaseFlow,
        ) { enabled, status, phase ->
            val isBlocked = !pluginManager.isPluginInstallEnabled()
            resolvePluginUiState(
                featureEnabled = enabled,
                pluginStatus = status,
                installPhase = phase,
                installBlocked = isBlocked,
                blockReason = null, // reason resolved at UI layer via string resource
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
