package eu.kanade.tachiyomi.data.translation

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class TranslationRunTelemetryTest {

    @Test
    fun `Firebase payload uses only approved bounded fields`() {
        val parameters = TranslationRunEvent(
            provider = "CLAUDE",
            model = "claude-3",
            targetLanguage = "en",
            totalPages = 3,
            resolvedPages = 2,
            retryCount = 1,
            durationMs = 42,
            terminalStatus = TranslationRunStatus.INCOMPLETE,
            outcomeCounts = mapOf(
                TranslationPageOutcome.NO_TEXT to 1,
                TranslationPageOutcome.TRANSLATED to 1,
                TranslationPageOutcome.PROVIDER_FAILURE to 1,
            ),
        ).toFirebaseParameters()

        assertEquals(
            setOf(
                "provider",
                "model",
                "target_language",
                "total_pages",
                "resolved_pages",
                "retry_count",
                "duration_ms",
                "terminal_status",
                "outcome_translated",
                "outcome_no_text",
                "outcome_legacy_stored",
                "outcome_unreadable_input",
                "outcome_provider_failure",
                "outcome_invalid_provider_output",
                "outcome_render_failure",
                "outcome_storage_failure",
                "outcome_not_attempted",
            ),
            parameters.keys,
        )
        assertFalse(
            parameters.keys.any {
                it in setOf(
                    "title",
                    "chapter",
                    "url",
                    "image",
                    "original_text",
                    "translated_text",
                    "raw_response",
                    "error",
                    "api_key",
                )
            },
        )
        assertEquals("incomplete", parameters["terminal_status"])
        assertEquals(1, parameters["outcome_provider_failure"])
    }

    @Test
    fun `no-op telemetry accepts a run without Firebase`() {
        assertDoesNotThrow {
            NoOpTranslationRunTelemetry.record(
                TranslationRunEvent(
                    provider = "CLAUDE",
                    model = "claude-3",
                    targetLanguage = "en",
                    totalPages = 1,
                    resolvedPages = 0,
                    retryCount = 0,
                    durationMs = 0,
                    terminalStatus = TranslationRunStatus.FAILED,
                    outcomeCounts = emptyMap(),
                ),
            )
        }
    }
}
