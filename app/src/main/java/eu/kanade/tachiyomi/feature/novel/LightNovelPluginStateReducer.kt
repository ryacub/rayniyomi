package eu.kanade.tachiyomi.feature.novel

/**
 * Resolves the current [LightNovelPluginUiState] from discrete input signals.
 *
 * Priority order (first match wins):
 * 1. Feature disabled -> Disabled
 * 2. Install blocked by policy -> Blocked
 * 3. Download in-flight -> Downloading
 * 4. OS installer launched -> Installing
 * 5. Not installed -> Missing
 * 6. Installed but incompatible -> Incompatible
 * 7. All clear -> Ready
 */
fun resolvePluginUiState(
    featureEnabled: Boolean,
    pluginStatus: LightNovelPluginManager.PluginStatus,
    installPhase: InstallPhase,
    installBlocked: Boolean,
    blockReason: String?,
): LightNovelPluginUiState {
    if (!featureEnabled) return LightNovelPluginUiState.Disabled
    if (installBlocked) return LightNovelPluginUiState.Blocked(blockReason.orEmpty())

    return when (installPhase) {
        InstallPhase.DOWNLOADING -> LightNovelPluginUiState.Downloading
        InstallPhase.INSTALLING -> LightNovelPluginUiState.Installing
        InstallPhase.IDLE -> when {
            !pluginStatus.installed -> LightNovelPluginUiState.Missing
            !pluginStatus.signedAndTrusted -> LightNovelPluginUiState.Incompatible(IncompatibleReason.UNTRUSTED)
            !pluginStatus.compatible -> LightNovelPluginUiState.Incompatible(IncompatibleReason.API_MISMATCH)
            else -> LightNovelPluginUiState.Ready
        }
    }
}

/** The current phase of the plugin install/update pipeline. */
enum class InstallPhase {
    IDLE,
    DOWNLOADING,
    INSTALLING,
}
