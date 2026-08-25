package eu.kanade.tachiyomi.ui.entries.manga

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.data.translation.TranslationState
import tachiyomi.domain.items.chapter.model.Chapter

@Immutable
data class ChapterTranslationProgress(
    val chapterId: Long,
    val chapterName: String,
    val currentPage: Int,
    val totalPages: Int,
)

@Immutable
data class TranslationSummary(
    val translatingCount: Int,
    val completedCount: Int,
    val failedCount: Int,
    val chapters: List<ChapterTranslationProgress>,
)

/**
 * Builds a translation progress summary for the given manga chapters.
 *
 * Returns null when no chapter is currently translating or failed, so the
 * summary card disappears once every job reaches a terminal, non-failed state.
 * States for chapters outside the list are ignored.
 */
fun translationSummaryFrom(
    states: Map<Long, TranslationState>,
    chapters: List<Chapter>,
): TranslationSummary? {
    var translatingCount = 0
    var completedCount = 0
    var failedCount = 0
    val rows = mutableListOf<ChapterTranslationProgress>()

    for (chapter in chapters) {
        when (val state = states[chapter.id]) {
            TranslationState.Translated -> {
                completedCount++
            }
            is TranslationState.Translating -> {
                translatingCount++
                rows += ChapterTranslationProgress(
                    chapterId = chapter.id,
                    chapterName = chapter.name,
                    currentPage = state.currentPage,
                    totalPages = state.totalPages,
                )
            }
            is TranslationState.Error -> {
                failedCount++
                rows += ChapterTranslationProgress(
                    chapterId = chapter.id,
                    chapterName = chapter.name,
                    currentPage = 0,
                    totalPages = 0,
                )
            }
            null,
            TranslationState.Idle,
            -> {}
        }
    }

    if (translatingCount == 0 && failedCount == 0) return null

    return TranslationSummary(
        translatingCount = translatingCount,
        completedCount = completedCount,
        failedCount = failedCount,
        chapters = rows,
    )
}
