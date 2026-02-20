package eu.kanade.tachiyomi.feature.novel

/**
 * Single source of truth for how the light novel plugin should be rendered in the UI.
 *
 * Each variant maps to exactly one visual treatment and one user action (or passive status).
 */
sealed interface LightNovelPluginUiState {
    /** Feature toggle is off. UI hides the light novel entry entirely. */
    data object Disabled : LightNovelPluginUiState

    /** Feature enabled, plugin not installed. Action: "Install". */
    data object Missing : LightNovelPluginUiState

    /**
     * Plugin installed but not usable (wrong API version, untrusted signature).
     * Action: "Update" or "Reinstall".
     */
    data class Incompatible(val reason: IncompatibleReason) : LightNovelPluginUiState

    /** APK download in flight. Passive: indeterminate progress. */
    data object Downloading : LightNovelPluginUiState

    /** OS package installer dialog launched. Passive: "Waiting for install...". */
    data object Installing : LightNovelPluginUiState

    /** Installed, trusted, compatible. Action: "Open" / launch library. */
    data object Ready : LightNovelPluginUiState

    /**
     * Policy or build config prevents installation.
     * Shows reason + optional "Learn more" action.
     */
    data class Blocked(val reason: String) : LightNovelPluginUiState
}

/** Why the installed plugin is not usable. Drives subtitle text and remediation label. */
enum class IncompatibleReason {
    /** Signature verification failed. Action: "Reinstall". */
    UNTRUSTED,

    /** API version mismatch. Action: "Update app" or "Update plugin". */
    API_MISMATCH,
}
