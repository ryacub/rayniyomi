package eu.kanade.tachiyomi.data.translation

import kotlinx.serialization.Serializable

/** Outcome recorded for one source page in a translation run. */
@Serializable
enum class TranslationPageOutcome {
    TRANSLATED,
    NO_TEXT,
    LEGACY_STORED,
    UNREADABLE_INPUT,
    PROVIDER_FAILURE,
    INVALID_PROVIDER_OUTPUT,
    RENDER_FAILURE,
    STORAGE_FAILURE,
    NOT_ATTEMPTED,
}

fun TranslationPageOutcome.isResolved(): Boolean = when (this) {
    TranslationPageOutcome.TRANSLATED,
    TranslationPageOutcome.NO_TEXT,
    TranslationPageOutcome.LEGACY_STORED,
    -> true
    TranslationPageOutcome.UNREADABLE_INPUT,
    TranslationPageOutcome.PROVIDER_FAILURE,
    TranslationPageOutcome.INVALID_PROVIDER_OUTPUT,
    TranslationPageOutcome.RENDER_FAILURE,
    TranslationPageOutcome.STORAGE_FAILURE,
    TranslationPageOutcome.NOT_ATTEMPTED,
    -> false
}

@Serializable
data class TranslationCoverage(
    val totalPages: Int,
    val outcomes: Map<Int, TranslationPageOutcome>,
)
