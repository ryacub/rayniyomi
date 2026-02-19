package eu.kanade.tachiyomi.feature.novel

/**
 * Represents the lifecycle state of the light-novel plugin as observed by the host app.
 *
 * Transitions follow this diagram:
 * ```
 * Uninstalled ──reinstall──► Ready
 * Ready ──uninstall──► Uninstalled
 * Ready ──data corrupt──► Corrupted
 * Corrupted ──clearAndRecover──► Ready
 * ```
 *
 * The host should never throw when transitioning between states. All failure paths
 * must land in [Uninstalled] or [Corrupted] and present a recovery option to the user.
 */
sealed interface PluginLifecycleState {

    /** True when the plugin is fully operational and the host may delegate work to it. */
    val isReady: Boolean

    /** The plugin package is not installed on this device. */
    data object Uninstalled : PluginLifecycleState {
        override val isReady: Boolean = false
    }

    /**
     * The plugin is installed but its persisted data is unreadable.
     *
     * @property reason A log-safe description of the parse failure. Never expose raw exception
     *   messages in user-visible UI; use a localised string resource instead.
     */
    data class Corrupted(val reason: String) : PluginLifecycleState {
        override val isReady: Boolean = false
    }

    /** The plugin is installed and its data passed integrity checks. */
    data object Ready : PluginLifecycleState {
        override val isReady: Boolean = true
    }
}

/**
 * Describes whether the plugin's on-disk data can be read without errors.
 *
 * This is a lightweight value type intended to be produced by a storage-layer integrity check
 * (e.g. [xyz.rayniyomi.plugin.lightnovel.data.NovelStorage.checkIntegrity]) and consumed by
 * [resolveLifecycleState].
 */
sealed interface PluginDataIntegrity {

    /** Data is absent or valid. */
    data object Ok : PluginDataIntegrity

    /** Data exists but could not be deserialised. */
    data class Corrupt(val reason: String) : PluginDataIntegrity
}

/**
 * Pure function that maps installation presence + data integrity to a [PluginLifecycleState].
 *
 * This is a top-level function so it can be tested in isolation without an Android context.
 *
 * Priority rules:
 * 1. If the plugin is **not installed**, return [PluginLifecycleState.Uninstalled] regardless
 *    of data integrity (stale data from a prior install is irrelevant).
 * 2. If installed but data is **corrupt**, return [PluginLifecycleState.Corrupted].
 * 3. Otherwise return [PluginLifecycleState.Ready].
 */
fun resolveLifecycleState(
    isInstalled: Boolean,
    dataIntegrity: PluginDataIntegrity,
): PluginLifecycleState {
    if (!isInstalled) return PluginLifecycleState.Uninstalled
    return when (dataIntegrity) {
        is PluginDataIntegrity.Ok -> PluginLifecycleState.Ready
        is PluginDataIntegrity.Corrupt -> PluginLifecycleState.Corrupted(reason = dataIntegrity.reason)
    }
}
