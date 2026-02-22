package eu.kanade.tachiyomi.feature.novel

internal enum class LightNovelPluginCompatibilityResult {
    COMPATIBLE,
    API_MISMATCH,
    HOST_TOO_OLD,
    HOST_TOO_NEW,
}

internal fun normalizeTargetHostVersion(targetHostVersion: Long?): Long? =
    targetHostVersion?.takeIf { it > 0L }

/**
 * Evaluates plugin compatibility contract between host app and Light Novel plugin.
 *
 * `targetHostVersion = null` (or <= 0) means the plugin has no upper host-version bound.
 */
internal fun evaluateLightNovelPluginCompatibility(
    pluginApiVersion: Int,
    minHostVersion: Long,
    targetHostVersion: Long?,
    hostVersionCode: Long,
    expectedPluginApiVersion: Int,
): LightNovelPluginCompatibilityResult {
    if (pluginApiVersion != expectedPluginApiVersion) {
        return LightNovelPluginCompatibilityResult.API_MISMATCH
    }
    if (hostVersionCode < minHostVersion) {
        return LightNovelPluginCompatibilityResult.HOST_TOO_OLD
    }
    val normalizedTargetHostVersion = normalizeTargetHostVersion(targetHostVersion)
    if (normalizedTargetHostVersion != null && hostVersionCode > normalizedTargetHostVersion) {
        return LightNovelPluginCompatibilityResult.HOST_TOO_NEW
    }
    return LightNovelPluginCompatibilityResult.COMPATIBLE
}
