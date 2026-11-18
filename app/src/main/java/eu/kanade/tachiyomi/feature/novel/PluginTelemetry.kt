package eu.kanade.tachiyomi.feature.novel

import logcat.LogPriority
import logcat.logcat

// ---------------------------------------------------------------------------
// API types
// ---------------------------------------------------------------------------

/**
 * Stages of the plugin distribution and activation pipeline.
 * Each stage maps to a discrete operation in [LightNovelPluginManager].
 */
internal enum class PluginStage {
    /** Remote manifest retrieval. */
    FETCH,

    /** Signature pinning and package-name validation. */
    VERIFY,

    /** APK download and system installer handoff. */
    INSTALL,

    /** Post-install IPC binding confirmation. */
    HANDSHAKE,
}

/**
 * Outcome of a plugin pipeline operation.
 *
 * Use [Success] for happy-path completions.
 * Use [Failure] with a [PluginFailureReason] and [isFatal] flag
 * to distinguish recoverable from fatal errors.
 */
internal sealed class PluginResult {
    data object Success : PluginResult()

    data class Failure(
        val reason: PluginFailureReason,
        val isFatal: Boolean,
    ) : PluginResult()
}

/**
 * Structured taxonomy of plugin failure root causes.
 * Used in [PluginResult.Failure.reason] to enable actionable triage.
 */
internal enum class PluginFailureReason {
    /** Remote endpoint unreachable or timed out. */
    NETWORK_TIMEOUT,

    /** APK signature does not match pinned certificate set. */
    SIGNATURE_MISMATCH,

    /** Plugin API version or host version constraint not satisfied. */
    VERSION_INCOMPATIBLE,

    /** Manifest JSON malformed or missing required fields. */
    CORRUPT_MANIFEST,

    /** Downloaded APK binary is corrupt or could not be parsed. */
    CORRUPT_APK,

    /** IPC binding or handshake protocol rejected by plugin. */
    HANDSHAKE_REJECTED,

    /** Root cause could not be determined. */
    UNKNOWN,
}

// ---------------------------------------------------------------------------
// Telemetry recorder
// ---------------------------------------------------------------------------

/**
 * Writes plugin pipeline events to logcat.
 */
internal class PluginTelemetry {

    private companion object {
        private const val LOG_TAG = "PLUGIN_TELEMETRY"
    }

    /**
     * Log a pipeline event.
     *
     * @param stage Which stage of the plugin pipeline this event belongs to.
     * @param result Outcome of the operation.
     * @param channel Distribution channel (currently `"stable"`), or null if not applicable.
     * @param durationMs Operation duration in milliseconds, or null if not measured.
     * @param enabled Predicate evaluated at call-site; when it returns `false` the event is
     *   silently discarded. Pass `{ isPluginInstallEnabled() }` from [LightNovelPluginManager]
     *   to suppress telemetry when the install path is disabled.
     */
    fun recordEvent(
        stage: PluginStage,
        result: PluginResult,
        channel: String?,
        durationMs: Long? = null,
        enabled: () -> Boolean = { true },
    ) {
        if (!enabled()) return

        val durationStr = if (durationMs != null) " durationMs=$durationMs" else ""
        when (result) {
            is PluginResult.Success -> {
                logcat(LOG_TAG, LogPriority.INFO) {
                    "stage=$stage result=Success channel=$channel$durationStr"
                }
            }
            is PluginResult.Failure -> {
                val priority = if (result.isFatal) LogPriority.ERROR else LogPriority.WARN
                logcat(LOG_TAG, priority) {
                    "stage=$stage result=Failure reason=${result.reason.name} fatal=${result.isFatal} channel=$channel$durationStr"
                }
            }
        }
    }
}
