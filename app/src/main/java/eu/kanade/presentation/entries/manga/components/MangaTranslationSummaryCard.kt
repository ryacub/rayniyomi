package eu.kanade.presentation.entries.manga.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.entries.manga.ChapterTranslationProgress
import eu.kanade.tachiyomi.ui.entries.manga.TranslationSummary
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Collapsible summary of the chapter translation jobs running for this manga. The collapsed row
 * shows the job counts; expanding lists one row per translating or failed chapter.
 */
@Composable
fun MangaTranslationSummaryCard(
    summary: TranslationSummary,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onToggleExpanded)
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(AYMR.strings.translation_summary_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    AYMR.strings.translation_summary_counts,
                    summary.translatingCount,
                    summary.completedCount,
                    summary.failedCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedVisibility(visible = expanded) {
                Column {
                    summary.chapters.forEach { chapter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            Text(
                                text = chapter.chapterName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            if (chapter.isFailed) {
                                Text(
                                    text = stringResource(AYMR.strings.translation_status_failed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else if (chapter.isIncomplete) {
                                Text(
                                    text = stringResource(
                                        AYMR.strings.translation_incomplete,
                                        chapter.currentPage,
                                        chapter.totalPages,
                                        chapter.unresolvedPages.joinToString(", "),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else if (chapter.totalPages > 0) {
                                Text(
                                    text = stringResource(
                                        AYMR.strings.translation_progress,
                                        chapter.currentPage,
                                        chapter.totalPages,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun MangaTranslationSummaryCardPreview() {
    MaterialTheme {
        MangaTranslationSummaryCard(
            summary = TranslationSummary(
                translatingCount = 2,
                completedCount = 1,
                failedCount = 1,
                chapters = listOf(
                    ChapterTranslationProgress(
                        chapterId = 1,
                        chapterName = "Chapter 12",
                        currentPage = 7,
                        totalPages = 20,
                    ),
                    ChapterTranslationProgress(
                        chapterId = 2,
                        chapterName = "Chapter 13",
                        currentPage = 14,
                        totalPages = 28,
                    ),
                    ChapterTranslationProgress(
                        chapterId = 4,
                        chapterName = "Chapter 15",
                        currentPage = 0,
                        totalPages = 0,
                        isFailed = true,
                    ),
                ),
            ),
            expanded = true,
            onToggleExpanded = {},
        )
    }
}
