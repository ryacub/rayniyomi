package eu.kanade.tachiyomi.feature.novel

import logcat.LogPriority
import logcat.logcat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

// ---------------------------------------------------------------------------
// Public API types
// ---------------------------------------------------------------------------

/**
 * Stages of the plugin distribution and activation pipeline.
 * Each stage maps to a discrete operation in [LightNovelPluginManager].
 */
public enum class PluginStage {
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
 * Use [Failure] with a [PluginFailureReason] name and [isFatal] flag
 * to distinguish recoverable from fatal errors.
 */
public sealed class PluginResult {
    public data object Success : PluginResult()

    public data class Failure(
        val reason: String,
        val isFatal: Boolean,
    ) : PluginResult()
}

/**
 * Structured taxonomy of plugin failure root causes.
 * Used in [PluginResult.Failure.reason] to enable actionable triage.
 */
public enum class PluginFailureReason {
    /** Remote endpoint unreachable or timed out. */
    NETWORK_TIMEOUT,

    /** APK signature does not match pinned certificate set. */
    SIGNATURE_MISMATCH,

    /** Plugin API version or host version constraint not satisfied. */
    VERSION_INCOMPATIBLE,

    /** Manifest JSON malformed or missing required fields. */
    CORRUPT_MANIFEST,

    /** IPC binding or handshake protocol rejected by plugin. */
    HANDSHAKE_REJECTED,

    /** Root cause could not be determined. */
    UNKNOWN,
}

/**
 * Threshold alert emitted by [PluginTelemetry.getThresholdAlert]
 * when a stage's failure rate exceeds the configured threshold.
 *
 * @property stage The pipeline stage that crossed the threshold.
 * @property failureRate Failure count divided by total sample count (0.0–1.0).
 * @property sampleCount Total events recorded for this stage.
 */
public data class ThresholdAlert(
    val stage: PluginStage,
    val failureRate: Double,
    val sampleCount: Int,
)

/**
 * Immutable snapshot of per-stage event counters.
 *
 * @property successCount Number of successful events.
 * @property failureCount Number of failed events.
 */
public data class StageCounters(
    val successCount: Int,
    val failureCount: Int,
) {
    /** Total number of events (success + failure). */
    val total: Int get() = successCount + failureCount
}

// ---------------------------------------------------------------------------
// Telemetry recorder
// ---------------------------------------------------------------------------

/**
 * Lightweight, in-process telemetry recorder for plugin pipeline events.
 *
 * All counters are reset on process restart; this is intentional — the
 * goal is to detect regression spikes within a session, not persistent
 * historical analytics (use a backend service for that).
 *
 * Thread-safe: atomic counter updates, ConcurrentHashMap for stage buckets.
 *
 * ### Log format (logcat / structured output)
 * ```
 * PLUGIN_TELEMETRY stage=FETCH result=Success channel=stable
 * PLUGIN_TELEMETRY stage=INSTALL result=Failure reason=NETWORK_TIMEOUT fatal=false channel=null
 * PLUGIN_TELEMETRY_ALERT stage=INSTALL failureRate=0.80 samples=5
 * ```
 */
public class PluginTelemetry {

    private companion object {
        /** Alert when more than this fraction of samples are failures. */
        private const val FAILURE_RATE_THRESHOLD = 0.5

        /** Minimum samples required before alert evaluation. */
        private const val MIN_SAMPLE_COUNT = 5

        private const val LOG_TAG = "PLUGIN_TELEMETRY"
        private const val ALERT_TAG = "PLUGIN_TELEMETRY_ALERT"
    }

    // ConcurrentHashMap + AtomicIntegers give lock-free per-cell updates.
    private val successCounters = ConcurrentHashMap<PluginStage, AtomicInteger>().also { map ->
        PluginStage.entries.forEach { map[it] = AtomicInteger(0) }
    }
    private val failureCounters = ConcurrentHashMap<PluginStage, AtomicInteger>().also { map ->
        PluginStage.entries.forEach { map[it] = AtomicInteger(0) }
    }

    /**
     * Record a pipeline event and update in-memory counters.
     *
     * @param stage Which stage of the plugin pipeline this event belongs to.
     * @param result Outcome of the operation.
     * @param channel Distribution channel (e.g. `"stable"`, `"beta"`), or null if not applicable.
     * @param enabled Predicate evaluated at call-site; when it returns `false` the event is
     *   silently discarded. Pass `{ isPluginInstallEnabled() }` from [LightNovelPluginManager]
     *   to suppress telemetry when the install path is disabled.
     */
    public fun recordEvent(
        stage: PluginStage,
        result: PluginResult,
        channel: String?,
        enabled: () -> Boolean = { true },
    ) {
        if (!enabled()) return

        when (result) {
            is PluginResult.Success -> {
                successCounters[stage]!!.incrementAndGet()
                logcat(LOG_TAG, LogPriority.INFO) {
                    "stage=$stage result=Success channel=$channel"
                }
            }
            is PluginResult.Failure -> {
                failureCounters[stage]!!.incrementAndGet()
                val priority = if (result.isFatal) LogPriority.ERROR else LogPriority.WARN
                logcat(LOG_TAG, priority) {
                    "stage=$stage result=Failure reason=${result.reason} fatal=${result.isFatal} channel=$channel"
                }
            }
        }
    }

    /**
     * Returns a [ThresholdAlert] if the failure rate for [stage] exceeds 50 %
     * and at least [MIN_SAMPLE_COUNT] events have been recorded.
     * Returns `null` if the sample size is too small or the failure rate is within bounds.
     */
    public fun getThresholdAlert(stage: PluginStage): ThresholdAlert? {
        val counters = getCounters(stage)
        val total = counters.total
        if (total < MIN_SAMPLE_COUNT) return null

        val failureRate = counters.failureCount.toDouble() / total
        if (failureRate <= FAILURE_RATE_THRESHOLD) return null

        val alert = ThresholdAlert(
            stage = stage,
            failureRate = failureRate,
            sampleCount = total,
        )
        logcat(ALERT_TAG, LogPriority.ERROR) {
            "stage=$stage failureRate=${"%.2f".format(failureRate)} samples=$total"
        }
        return alert
    }

    /**
     * Returns an immutable snapshot of the current success/failure counters for [stage].
     * Useful in tests and debug UI.
     */
    public fun getCounters(stage: PluginStage): StageCounters {
        return StageCounters(
            successCount = successCounters[stage]!!.get(),
            failureCount = failureCounters[stage]!!.get(),
        )
    }
}
