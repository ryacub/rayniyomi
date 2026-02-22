package eu.kanade.tachiyomi.feature.novel

/**
 * Performance budget thresholds for plugin operations.
 * Violations are logged at WARN level by [PluginPerformanceTracker].
 */
internal object PluginPerformanceBudgets {

    const val STARTUP_CONTRIBUTION_MS = 150L
    const val MANIFEST_FETCH_MS = 5_000L
    const val PLUGIN_INSTALL_MS = 30_000L
    const val FEATURE_GATE_CHECK_MS = 5L
    const val EPUB_IMPORT_MS = 10_000L
}
