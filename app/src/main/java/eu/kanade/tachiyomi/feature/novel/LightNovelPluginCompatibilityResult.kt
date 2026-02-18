package eu.kanade.tachiyomi.feature.novel

internal enum class LightNovelPluginCompatibilityResult {
    COMPATIBLE,
    API_MISMATCH,
    HOST_TOO_OLD,
    HOST_TOO_NEW,
}

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
    if (targetHostVersion != null && hostVersionCode > targetHostVersion) {
        return LightNovelPluginCompatibilityResult.HOST_TOO_NEW
    }
    return LightNovelPluginCompatibilityResult.COMPATIBLE
}
