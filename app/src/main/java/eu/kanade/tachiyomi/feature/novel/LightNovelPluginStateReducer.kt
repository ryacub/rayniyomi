package eu.kanade.tachiyomi.feature.novel

/**
 * Resolves the current [LightNovelPluginUiState] from discrete input signals.
 *
 * Priority order (first match wins):
 * 1. Feature disabled -> Disabled
 * 2. Download in-flight -> Downloading
 * 3. OS installer launched -> Installing
 * 4. Plugin installed and ready -> Ready
 * 5. Plugin installed but untrusted -> Incompatible(UNTRUSTED)
 * 6. Plugin installed but wrong API -> Incompatible(API_MISMATCH)
 * 7. Most recent install attempt failed -> InstallFailed(code)
 * 8. Plugin not installed and install blocked -> Blocked
 * 9. Plugin not installed -> Missing
 *
 * NOTE: [installBlocked] only applies when the plugin is not installed. An already-installed
 * plugin that is ready or incompatible is reported as such regardless of install policy. This
 * ensures release builds where install is disabled still show Ready for users who have the
 * plugin installed via sideload or a prior build.
 *
 * NOTE: [lastInstallError] only surfaces when the plugin is not already installed/ready or
 * installed-but-incompatible. Those installed states are more specific and take priority, so a
 * stale failure never masks a plugin that has since become usable. The install flow clears the
 * error when a fresh attempt starts, so the reported failure is always the latest one.
 */
fun resolvePluginUiState(
    featureEnabled: Boolean,
    pluginStatus: LightNovelPluginManager.PluginStatus,
    installPhase: InstallPhase,
    installBlocked: Boolean,
    blockReason: String?,
    lastInstallError: LightNovelPluginManager.InstallErrorCode? = null,
): LightNovelPluginUiState {
    if (!featureEnabled) return LightNovelPluginUiState.Disabled

    return when (installPhase) {
        InstallPhase.DOWNLOADING -> LightNovelPluginUiState.Downloading
        InstallPhase.INSTALLING -> LightNovelPluginUiState.Installing
        InstallPhase.IDLE -> when {
            pluginStatus.installed && pluginStatus.signedAndTrusted && pluginStatus.compatible ->
                LightNovelPluginUiState.Ready
            pluginStatus.installed && !pluginStatus.signedAndTrusted ->
                LightNovelPluginUiState.Incompatible(IncompatibleReason.UNTRUSTED)
            pluginStatus.installed && !pluginStatus.compatible ->
                LightNovelPluginUiState.Incompatible(IncompatibleReason.API_MISMATCH)
            lastInstallError != null ->
                LightNovelPluginUiState.InstallFailed(lastInstallError)
            installBlocked ->
                LightNovelPluginUiState.Blocked(blockReason.orEmpty())
            else ->
                LightNovelPluginUiState.Missing
        }
    }
}

/** The current phase of the plugin install/update pipeline. */
enum class InstallPhase {
    IDLE,
    DOWNLOADING,
    INSTALLING,
}
