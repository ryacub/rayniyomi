package eu.kanade.presentation.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.library.LibraryUpdateSkippedEntry
import eu.kanade.tachiyomi.data.library.LibraryUpdateSummary
import eu.kanade.tachiyomi.data.library.LibraryUpdateSummaryEntry
import eu.kanade.tachiyomi.data.library.LibraryUpdateSummaryOutcome
import eu.kanade.tachiyomi.data.library.labelRes
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.text.DateFormat
import java.util.Date

@Composable
internal fun LibraryUpdateSummaryRow(
    summary: LibraryUpdateSummary,
    onClickInfo: () -> Unit,
    onClickClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = summary.message(),
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            IconButton(onClick = onClickInfo) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(MR.strings.library_update_summary_details),
                )
            }
            IconButton(onClick = onClickClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(MR.strings.action_close),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryUpdateSummarySheet(
    summary: LibraryUpdateSummary,
    onDismissRequest: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var updatedExpanded by remember(summary.executionId) { mutableStateOf(true) }
    var skippedExpanded by remember(summary.executionId) { mutableStateOf(true) }
    var failedExpanded by remember(summary.executionId) { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismissRequest) {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = stringResource(MR.strings.library_update_summary_details),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = summary.message(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            MR.strings.library_update_summary_finished,
                            DateFormat.getDateTimeInstance().format(Date(summary.finishedAt)),
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (summary.updated.isNotEmpty()) {
                item {
                    SummarySectionHeader(
                        title = stringResource(
                            MR.strings.library_update_summary_updated,
                            summary.updatedCount,
                        ),
                        expanded = updatedExpanded,
                        onClick = { updatedExpanded = !updatedExpanded },
                    )
                }
                if (updatedExpanded) {
                    items(summary.updated, key = { "updated-${it.id}" }) { entry ->
                        SummaryEntry(entry, onOpenEntry)
                    }
                }
            }
            if (summary.skipped.isNotEmpty()) {
                item {
                    SummarySectionHeader(
                        title = stringResource(
                            MR.strings.library_update_summary_skipped,
                            summary.skippedCount,
                        ),
                        expanded = skippedExpanded,
                        onClick = { skippedExpanded = !skippedExpanded },
                    )
                }
                if (skippedExpanded) {
                    items(summary.skipped, key = { "skipped-${it.entry.id}" }) { entry ->
                        SkippedEntry(entry, onOpenEntry)
                    }
                    item {
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        ) {
                            Text(stringResource(MR.strings.library_update_summary_settings))
                        }
                    }
                }
            }
            if (summary.failed.isNotEmpty()) {
                item {
                    SummarySectionHeader(
                        title = stringResource(
                            MR.strings.library_update_summary_failed,
                            summary.failedCount,
                        ),
                        expanded = failedExpanded,
                        onClick = { failedExpanded = !failedExpanded },
                    )
                }
                if (failedExpanded) {
                    summary.failed.groupBy { it.error }.forEach { (error, entries) ->
                        item { FailedErrorHeader(error) }
                        items(entries, key = { "failed-${it.entry.id}" }) { failedEntry ->
                            SummaryEntry(failedEntry.entry, onOpenEntry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummarySectionHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
        )
    }
}

@Composable
private fun SummaryEntry(
    entry: LibraryUpdateSummaryEntry,
    onOpenEntry: (Long) -> Unit,
) {
    ListItem(
        headlineContent = { Text(entry.title) },
        modifier = Modifier.clickable { onOpenEntry(entry.id) },
    )
}

@Composable
private fun SkippedEntry(
    skipped: LibraryUpdateSkippedEntry,
    onOpenEntry: (Long) -> Unit,
) {
    ListItem(
        headlineContent = { Text(skipped.entry.title) },
        supportingContent = { Text(stringResource(skipped.reason.labelRes)) },
        modifier = Modifier.clickable { onOpenEntry(skipped.entry.id) },
    )
}

@Composable
private fun FailedErrorHeader(error: String?) {
    Text(
        text = error ?: stringResource(MR.strings.unknown_error),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Medium,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun LibraryUpdateSummary.message(): String {
    val resource = when (outcome) {
        LibraryUpdateSummaryOutcome.COMPLETED -> MR.strings.library_update_summary_completed
        LibraryUpdateSummaryOutcome.CANCELLED -> MR.strings.library_update_summary_cancelled
        LibraryUpdateSummaryOutcome.INTERRUPTED -> MR.strings.library_update_summary_interrupted
    }
    return stringResource(resource, updatedCount, skippedCount, failedCount)
}
