package eu.kanade.tachiyomi.feature.novel

import logcat.LogPriority
import logcat.logcat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

/**
 * Threshold alert returned by [PluginTelemetry.getThresholdAlert]
 * when a stage's failure rate exceeds the configured threshold.
 *
 * @property stage The pipeline stage that crossed the threshold.
 * @property failureRate Failure count divided by total sample count (0.0–1.0).
 * @property failureCount Raw number of failures recorded for this stage.
 * @property sampleCount Total events recorded for this stage.
 */
internal data class ThresholdAlert(
    val stage: PluginStage,
    val failureRate: Double,
    val failureCount: Int,
    val sampleCount: Int,
)

/**
 * Immutable snapshot of per-stage event counters.
 *
 * @property successCount Number of successful events.
 * @property failureCount Number of failed events.
 */
internal data class StageCounters(
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
 * Thread-safe: atomic counter updates via AtomicInteger; counter snapshots
 * are synchronized to avoid TOCTOU races between success and failure reads.
 *
 * ### Log format (logcat / structured output)
 * ```
 * PLUGIN_TELEMETRY stage=FETCH result=Success channel=stable
 * PLUGIN_TELEMETRY stage=INSTALL result=Failure reason=NETWORK_TIMEOUT fatal=false channel=null
 * ```
 *
 * Threshold alerts are not logged here — callers should log [ThresholdAlert]
 * at a cadence appropriate for their context.
 */
internal class PluginTelemetry {

    private companion object {
        /** Alert when more than this fraction of samples are failures. */
        private const val FAILURE_RATE_THRESHOLD = 0.5

        /** Minimum samples required before alert evaluation. */
        private const val MIN_SAMPLE_COUNT = 5

        private const val LOG_TAG = "PLUGIN_TELEMETRY"
    }

    // ConcurrentHashMap + AtomicIntegers give lock-free per-cell updates.
    // All stage keys are pre-populated so !! assertions on lookup are safe.
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
    fun recordEvent(
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
                    "stage=$stage result=Failure reason=${result.reason.name} fatal=${result.isFatal} channel=$channel"
                }
            }
        }
    }

    /**
     * Returns a [ThresholdAlert] if the failure rate for [stage] exceeds 50%
     * and at least [MIN_SAMPLE_COUNT] events have been recorded; null otherwise.
     *
     * This is a pure query — it does not emit any log output. Callers that
     * want to surface the alert (e.g. via logcat or UI) should do so after
     * receiving a non-null result.
     */
    fun getThresholdAlert(stage: PluginStage): ThresholdAlert? {
        val counters = getCounters(stage)
        val total = counters.total
        if (total < MIN_SAMPLE_COUNT) return null

        val failureRate = counters.failureCount.toDouble() / total
        if (failureRate <= FAILURE_RATE_THRESHOLD) return null

        return ThresholdAlert(
            stage = stage,
            failureRate = failureRate,
            failureCount = counters.failureCount,
            sampleCount = total,
        )
    }

    /**
     * Returns an immutable, atomically-consistent snapshot of the current
     * success/failure counters for [stage]. Both counters are read under a
     * synchronized block to prevent TOCTOU races.
     *
     * Useful in tests and debug UI.
     */
    fun getCounters(stage: PluginStage): StageCounters {
        return synchronized(this) {
            StageCounters(
                successCount = successCounters[stage]!!.get(),
                failureCount = failureCounters[stage]!!.get(),
            )
        }
    }

    /**
     * Formats [failureRate] as a locale-independent percentage string for
     * structured log output.
     */
    internal fun formatRate(failureRate: Double): String =
        String.format(Locale.ROOT, "%.2f", failureRate)
}
