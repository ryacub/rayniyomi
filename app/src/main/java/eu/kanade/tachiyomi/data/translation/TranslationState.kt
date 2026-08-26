package eu.kanade.tachiyomi.data.translation

/**
 * What a [TranslationState.Translating] chapter is doing right now.
 *
 * The phase never changes the progress count. [TranslationState.Translating.currentPage] is always
 * the number of finished pages, so the progress ring only moves forward.
 */
sealed interface TranslationPhase {
    /** Steady progress: the next unfinished page is being translated. */
    data object Progressing : TranslationPhase

    /**
     * A transient failure is being retried. [page] is the 1-based page number being retried,
     * matching user-facing numbering. This intentionally differs from the removed
     * retryingPage field, which was 0-based.
     */
    data class Retrying(val page: Int) : TranslationPhase
}

/**
 * State of translation for a chapter.
 */
sealed class TranslationState {
    data object Idle : TranslationState()

    /**
     * [currentPage] counts finished pages, never the page being attempted.
     */
    data class Translating(
        val currentPage: Int,
        val totalPages: Int,
        val phase: TranslationPhase = TranslationPhase.Progressing,
    ) : TranslationState()

    data object Translated : TranslationState()
    data class Error(val message: String) : TranslationState()
}
