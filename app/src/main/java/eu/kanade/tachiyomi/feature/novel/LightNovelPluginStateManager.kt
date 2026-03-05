package eu.kanade.tachiyomi.feature.novel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import eu.kanade.domain.novel.NovelFeaturePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.system.logcat
import java.io.Closeable

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
    private val appContext: Context,
    private val pluginManager: LightNovelPluginManager,
    private val preferences: NovelFeaturePreferences,
) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pluginPackageReceiver: BroadcastReceiver? = null

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
        registerPluginPackageReceiver()

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

    private fun registerPluginPackageReceiver() {
        if (pluginPackageReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                val packageName = intent.data?.schemeSpecificPart
                if (isLightNovelPluginPackageChange(intent.action, packageName)) {
                    refreshPluginStatus()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        pluginPackageReceiver = receiver
    }

    override fun close() {
        pluginManager.close()
        pluginPackageReceiver?.let { receiver ->
            runCatching { appContext.unregisterReceiver(receiver) }
            pluginPackageReceiver = null
        }
        scope.cancel()
    }
}

internal fun isLightNovelPluginPackageChange(action: String?, packageName: String?): Boolean {
    val relevantAction = action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REMOVED
    return relevantAction && packageName == LightNovelPluginManager.PLUGIN_PACKAGE_NAME
}
