package eu.kanade.tachiyomi.feature.novel

/**
 * Performance and battery budgets for the light novel plugin.
 *
 * These thresholds represent the maximum acceptable overhead that the plugin
 * may impose on the host application. Violations are logged at WARN level via
 * [PluginPerformanceTracker] to enable early detection of regressions.
 *
 * ## Budget categories
 *
 * - **Startup contribution**: Time added to cold-start by plugin initialization.
 * - **Manifest fetch**: Maximum time for remote manifest retrieval.
 * - **Plugin install**: Maximum time for APK download and install handoff.
 * - **Feature gate check**: Maximum time for feature flag evaluation.
 * - **Memory overhead**: Additional memory footprint when plugin is loaded.
 * - **EPUB import**: Maximum time for parsing and importing a single EPUB file.
 * - **Background wakeups**: Maximum number of background wake events per hour.
 *
 * ## Usage
 *
 * Use [PluginPerformanceTracker] to record operation durations:
 * ```
 * tracker.recordOperation(OperationCategory.STARTUP, durationMs)
 * ```
 *
 * The tracker will log violations when recorded durations exceed these budgets.
 */
internal object PluginPerformanceBudgets {

    /**
     * Maximum acceptable contribution to cold-start latency (milliseconds).
     *
     * This budget applies to plugin initialization that occurs on the critical
     * startup path. Any work that can be deferred or lazily loaded should not
     * count against this budget.
     */
    const val STARTUP_CONTRIBUTION_MS = 150L

    /**
     * Maximum acceptable time for remote manifest retrieval (milliseconds).
     *
     * This includes network request, JSON parsing, and basic validation.
     * Applies to both stable and beta channel manifest endpoints.
     */
    const val MANIFEST_FETCH_MS = 5_000L

    /**
     * Maximum acceptable time for plugin installation flow (milliseconds).
     *
     * This includes APK download, signature verification, and system installer
     * handoff. Does not include time spent in the system package installer UI.
     */
    const val PLUGIN_INSTALL_MS = 30_000L

    /**
     * Maximum acceptable time for feature gate evaluation (milliseconds).
     *
     * Feature gate checks occur on hot paths (e.g., UI rendering, navigation).
     * This budget enforces that flag evaluation remains negligible overhead.
     */
    const val FEATURE_GATE_CHECK_MS = 5L

    /**
     * Maximum acceptable memory overhead when plugin is loaded (megabytes).
     *
     * This budget represents the additional memory footprint attributed to
     * the plugin process and IPC infrastructure. Measured via PSS delta.
     */
    const val MEMORY_OVERHEAD_MB = 15L

    /**
     * Maximum acceptable time for EPUB file import (milliseconds).
     *
     * This applies to parsing and importing a single EPUB file via the
     * plugin's content provider interface. Larger files may legitimately
     * exceed this budget; track p95 latency to identify outliers.
     */
    const val EPUB_IMPORT_MS = 10_000L

    /**
     * Maximum acceptable background wake events per hour.
     *
     * Background wakeups drain battery by preventing the device from entering
     * deep sleep states. This budget limits the plugin's impact on idle power.
     */
    const val BACKGROUND_WAKEUPS_PER_HOUR = 2
}
