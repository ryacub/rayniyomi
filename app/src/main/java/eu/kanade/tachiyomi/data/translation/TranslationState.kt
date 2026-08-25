package eu.kanade.tachiyomi.data.translation

/**
 * State of translation for a chapter.
 */
sealed class TranslationState {
    data object Idle : TranslationState()
    data class Translating(
        val currentPage: Int,
        val totalPages: Int,
        val retryingPage: Int? = null,
    ) : TranslationState()
    data object Translated : TranslationState()
    data class Error(val message: String) : TranslationState()
}
