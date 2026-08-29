package eu.kanade.tachiyomi.data.translation

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.Locale

/** Terminal state recorded for one translation run. */
enum class TranslationRunStatus(val value: String) {
    TRANSLATED("translated"),
    INCOMPLETE("incomplete"),
    FAILED("failed"),
    CANCELLED("cancelled"),
}

/** Aggregate, content-free metrics for one [TranslationChapterRunner.run] call. */
data class TranslationRunEvent(
    val provider: String,
    val model: String,
    val targetLanguage: String,
    val totalPages: Int,
    val resolvedPages: Int,
    val retryCount: Int,
    val durationMs: Long,
    val terminalStatus: TranslationRunStatus,
    val outcomeCounts: Map<TranslationPageOutcome, Int>,
)

/** Boundary for recording translation metrics without coupling the runner to Firebase. */
fun interface TranslationRunTelemetry {
    fun record(event: TranslationRunEvent)
}

/** Used by tests and variants that do not initialize Firebase. */
object NoOpTranslationRunTelemetry : TranslationRunTelemetry {
    override fun record(event: TranslationRunEvent) = Unit
}

/** Firebase Analytics implementation for the aggregate translation event. */
class FirebaseTranslationRunTelemetry(
    private val analytics: FirebaseAnalytics,
) : TranslationRunTelemetry {

    override fun record(event: TranslationRunEvent) {
        val bundle = Bundle()
        event.toFirebaseParameters().forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Long -> bundle.putLong(key, value)
            }
        }
        analytics.logEvent(EVENT_NAME, bundle)
    }

    companion object {
        const val EVENT_NAME = "translation_run_finished"
    }
}

/** Creates Firebase telemetry only when the build has an initialized Firebase app. */
object TranslationRunTelemetryFactory {
    fun create(context: Context): TranslationRunTelemetry = runCatching {
        FirebaseApp.getApps(context).firstOrNull()?.let {
            FirebaseTranslationRunTelemetry(FirebaseAnalytics.getInstance(context))
        }
    }.getOrNull() ?: NoOpTranslationRunTelemetry
}

internal fun TranslationRunEvent.toFirebaseParameters(): Map<String, Any> = buildMap {
    put("provider", provider.take(MAX_ATTRIBUTE_LENGTH))
    put("model", model.take(MAX_ATTRIBUTE_LENGTH))
    put("target_language", targetLanguage.take(MAX_ATTRIBUTE_LENGTH))
    put("total_pages", totalPages)
    put("resolved_pages", resolvedPages)
    put("retry_count", retryCount)
    put("duration_ms", durationMs)
    put("terminal_status", terminalStatus.value)
    TranslationPageOutcome.entries.forEach { outcome ->
        put("outcome_${outcome.name.lowercase(Locale.ROOT)}", outcomeCounts[outcome] ?: 0)
    }
}

private const val MAX_ATTRIBUTE_LENGTH = 64
