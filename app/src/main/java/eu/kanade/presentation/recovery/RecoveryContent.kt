package eu.kanade.presentation.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.recovery.RecoveryMedia
import eu.kanade.tachiyomi.ui.recovery.RecoveryOriginal
import eu.kanade.tachiyomi.ui.recovery.RecoveryPreview
import eu.kanade.tachiyomi.ui.recovery.RecoveryScreenModel
import eu.kanade.tachiyomi.ui.recovery.RecoveryScreenModel.Step
import eu.kanade.tachiyomi.ui.recovery.RecoverySourceOption
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryReviewReason
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class RecoveryActions(
    val navigateUp: () -> Unit,
    val toggleOriginal: (Long) -> Unit,
    val toggleSource: (Long) -> Unit,
    val setKeepOriginal: (Boolean) -> Unit,
    val findReplacements: () -> Unit,
    val choose: (Long, RecoveryCandidate) -> Unit,
    val approve: (Long) -> Unit,
    val skip: (Long) -> Unit,
    val reopen: (Long) -> Unit,
    val searchAgain: (Long, String) -> Unit,
    val searchRemaining: () -> Unit,
    val apply: () -> Unit,
    val cancel: () -> Unit,
    val retryFailed: () -> Unit,
    val reviewUnresolved: () -> Unit,
    val startAnother: () -> Unit,
    val openTitle: (Long) -> Unit,
    val migrateAlone: (Long) -> Unit,
)

@Composable
fun RecoveryScreenContent(
    state: RecoveryScreenModel.State,
    media: RecoveryMedia,
    failedSourceName: String?,
    actions: RecoveryActions,
) {
    var confirming by remember { mutableStateOf(false) }
    var manualSearch by remember { mutableStateOf<RecoveryEntry?>(null) }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(MR.strings.recovery_title),
                navigateUp = actions.navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            RecoveryBottomBar(
                state = state,
                actions = actions,
                onApply = { confirming = true },
            )
        },
    ) { contentPadding ->
        when (state.step) {
            Step.Loading -> LoadingScreen(Modifier.padding(contentPadding))
            Step.Select -> SelectStep(state, failedSourceName, contentPadding, actions)
            Step.Review -> ReviewStep(state, media, contentPadding, actions, onSearch = { manualSearch = it })
            Step.Result -> ResultStep(state, contentPadding, actions)
        }
    }

    if (confirming) {
        ConfirmApplyDialog(
            readyCount = state.applicableCount,
            keepOriginal = state.keepOriginal,
            onDismiss = { confirming = false },
            onConfirm = {
                confirming = false
                actions.apply()
            },
        )
    }
    manualSearch?.let { entry ->
        ManualSearchDialog(
            initialQuery = entry.searchQuery,
            onDismiss = { manualSearch = null },
            onSearch = { query ->
                manualSearch = null
                actions.searchAgain(entry.id, query)
            },
        )
    }
}

@Composable
private fun RecoveryBottomBar(state: RecoveryScreenModel.State, actions: RecoveryActions, onApply: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.workError?.let {
            Text(stringResource(MR.strings.recovery_work_failed, it), color = MaterialTheme.colorScheme.error)
        }
        if (state.busy != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = actions.cancel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(MR.strings.action_cancel))
            }
            return@Column
        }
        when (state.step) {
            Step.Select -> Button(
                onClick = actions.findReplacements,
                enabled = state.selectedSources.isNotEmpty() && state.selectedOriginals.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(MR.strings.recovery_action_find)) }
            Step.Review -> {
                if (state.searchableCount > 0) {
                    OutlinedButton(onClick = actions.searchRemaining, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(MR.strings.recovery_action_search_remaining, state.searchableCount))
                    }
                }
                val ready = state.applicableCount
                Button(onClick = onApply, enabled = ready > 0, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(MR.strings.recovery_action_apply, ready))
                }
                TextButton(onClick = actions.startAnother, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(MR.strings.recovery_action_start_another))
                }
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectStep(
    state: RecoveryScreenModel.State,
    failedSourceName: String?,
    contentPadding: PaddingValues,
    actions: RecoveryActions,
) {
    LazyColumn(contentPadding = contentPadding) {
        item {
            Text(
                text = failedSourceName?.let { stringResource(MR.strings.recovery_intro, it) }
                    ?: stringResource(MR.strings.recovery_intro_generic),
                modifier = Modifier.padding(16.dp),
            )
        }
        item { SectionHeader(stringResource(MR.strings.recovery_section_sources)) }
        item {
            if (state.sources.isEmpty()) {
                Text(stringResource(MR.strings.recovery_no_sources), Modifier.padding(horizontal = 16.dp))
            }
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.sources.forEach { source ->
                    FilterChip(
                        selected = source.id in state.selectedSources,
                        onClick = { actions.toggleSource(source.id) },
                        label = { Text("${source.name} (${source.lang})") },
                    )
                }
            }
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(MR.strings.recovery_keep_original)) },
                supportingContent = { Text(stringResource(MR.strings.recovery_keep_original_summary)) },
                trailingContent = { Switch(checked = state.keepOriginal, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = state.keepOriginal,
                    role = Role.Switch,
                    onValueChange = actions.setKeepOriginal,
                ),
            )
        }
        item { SectionHeader(stringResource(MR.strings.recovery_section_titles)) }
        items(state.originals, key = { "original-${it.id}" }) { original ->
            val selected = original.id in state.selectedOriginals
            ListItem(
                headlineContent = { Text(original.title) },
                supportingContent = if (original.seasons) {
                    { Text(stringResource(MR.strings.recovery_state_unsupported)) }
                } else {
                    null
                },
                leadingContent = { Checkbox(checked = selected, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = selected,
                    role = Role.Checkbox,
                    onValueChange = { actions.toggleOriginal(original.id) },
                ),
            )
        }
    }
}

@Composable
private fun ReviewStep(
    state: RecoveryScreenModel.State,
    media: RecoveryMedia,
    contentPadding: PaddingValues,
    actions: RecoveryActions,
    onSearch: (RecoveryEntry) -> Unit,
) {
    val sourceNames = state.sources.associate { it.id to it.name }
    LazyColumn(contentPadding = contentPadding) {
        if (state.notInSession > 0) {
            item {
                Text(
                    text = pluralStringResource(
                        MR.plurals.recovery_not_in_session,
                        state.notInSession,
                        state.notInSession,
                    ),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(state.entries, key = { "entry-${it.id}" }) { entry ->
            ReviewRow(
                entry = entry,
                title = state.titleOf(entry),
                media = media,
                sourceNames = sourceNames,
                chosen = state.chosen[entry.id],
                preview = state.previews[entry.id],
                previewError = state.previewErrors[entry.id],
                canApprove = state.canApprove(entry.id),
                actions = actions,
                onSearch = { onSearch(entry) },
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun ReviewRow(
    entry: RecoveryEntry,
    title: String,
    media: RecoveryMedia,
    sourceNames: Map<Long, String>,
    chosen: RecoveryCandidate?,
    preview: RecoveryPreview?,
    previewError: String?,
    canApprove: Boolean,
    actions: RecoveryActions,
    onSearch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(entry.state.label()), style = MaterialTheme.typography.labelLarge)
        entry.error?.let { reason -> reasonText(reason)?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
        entry.searchErrors.forEach { (sourceId, message) ->
            Text(
                stringResource(
                    MR.strings.recovery_source_failed,
                    sourceNames[sourceId] ?: sourceId.toString(),
                    message,
                ),
                color = MaterialTheme.colorScheme.error,
            )
        }
        when (entry.state) {
            RecoveryState.UNSUPPORTED -> SeasonsHelp(entry, actions)
            RecoveryState.SELECTED,
            RecoveryState.NEEDS_REVIEW,
            RecoveryState.MISSING,
            RecoveryState.FAILED,
            RecoveryState.INTERRUPTED,
            -> {
                if (entry.choice == null) {
                    entry.candidates.forEach { candidate ->
                        CandidateRow(candidate, sourceNames, selected = candidate == chosen) {
                            actions.choose(entry.id, candidate)
                        }
                    }
                    PreviewSummary(media, preview, previewError)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { actions.approve(entry.id) }, enabled = canApprove) {
                            Text(stringResource(MR.strings.recovery_action_use_match))
                        }
                        TextButton(onClick = onSearch) {
                            Text(stringResource(MR.strings.recovery_action_search_manually))
                        }
                        TextButton(onClick = {
                            actions.skip(entry.id)
                        }) { Text(stringResource(MR.strings.recovery_action_skip)) }
                    }
                } else {
                    ChosenRow(entry, sourceNames, actions)
                }
            }
            RecoveryState.READY -> ChosenRow(entry, sourceNames, actions)
            RecoveryState.COMPLETED -> entry.resultId?.let { id ->
                TextButton(onClick = {
                    actions.openTitle(id)
                }) { Text(stringResource(MR.strings.recovery_action_open)) }
            }
            else -> Unit
        }
    }
}

@Composable
private fun SeasonsHelp(entry: RecoveryEntry, actions: RecoveryActions) {
    Text(stringResource(MR.strings.recovery_seasons_text))
    Text(stringResource(MR.strings.recovery_seasons_open), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(MR.strings.recovery_seasons_migrate), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(MR.strings.recovery_seasons_keep), style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = {
            actions.openTitle(entry.originalId)
        }) { Text(stringResource(MR.strings.recovery_action_open)) }
        TextButton(onClick = { actions.migrateAlone(entry.originalId) }) {
            Text(stringResource(MR.strings.recovery_action_migrate_alone))
        }
    }
}

@Composable
private fun CandidateRow(
    candidate: RecoveryCandidate,
    sourceNames: Map<Long, String>,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(candidate.title) },
        supportingContent = { Text(sourceNames[candidate.sourceId] ?: candidate.sourceId.toString()) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.toggleable(value = selected, role = Role.RadioButton, onValueChange = { onSelect() }),
    )
}

@Composable
private fun PreviewSummary(media: RecoveryMedia, preview: RecoveryPreview?, previewError: String?) {
    previewError?.let {
        Text(stringResource(MR.strings.recovery_preview_failed, it), color = MaterialTheme.colorScheme.error)
    }
    preview ?: return
    if (preview.destination.seasons) {
        Text(stringResource(MR.strings.recovery_seasons_candidate), color = MaterialTheme.colorScheme.error)
        return
    }
    val coverage = if (media == RecoveryMedia.MANGA) {
        MR.strings.recovery_coverage_chapters
    } else {
        MR.strings.recovery_coverage_episodes
    }
    Text(stringResource(coverage, preview.pairs.size, preview.originalItemCount))
}

@Composable
private fun ChosenRow(entry: RecoveryEntry, sourceNames: Map<Long, String>, actions: RecoveryActions) {
    val choice = entry.choice ?: return
    ListItem(
        headlineContent = { Text(choice.title) },
        supportingContent = { Text(sourceNames[choice.sourceId] ?: choice.sourceId.toString()) },
        trailingContent = {
            TextButton(onClick = {
                actions.reopen(entry.id)
            }) { Text(stringResource(MR.strings.recovery_action_change_match)) }
        },
    )
}

@Composable
private fun ResultStep(state: RecoveryScreenModel.State, contentPadding: PaddingValues, actions: RecoveryActions) {
    val summary = state.summary
    LazyColumn(contentPadding = contentPadding) {
        item {
            Text(
                text = if (summary.fullyRecovered) {
                    stringResource(MR.strings.recovery_result_full, summary.completed)
                } else {
                    stringResource(
                        MR.strings.recovery_result_partial,
                        summary.completed,
                        summary.needsAttention,
                        summary.skipped,
                    )
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp).semantics { heading() },
            )
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (summary.failed > 0) {
                    OutlinedButton(onClick = actions.retryFailed) {
                        Text(stringResource(MR.strings.recovery_action_retry_failed))
                    }
                }
                if (summary.unresolved + summary.unsupported > 0) {
                    OutlinedButton(onClick = actions.reviewUnresolved) {
                        Text(stringResource(MR.strings.recovery_action_review_unresolved))
                    }
                }
            }
        }
        items(state.entries, key = { "result-${it.id}" }) { entry ->
            val title = state.titleOf(entry)
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = {
                    Column {
                        Text(stringResource(entry.state.label()))
                        state.coverFailures[entry.id]?.let {
                            Text(stringResource(MR.strings.recovery_cover_failed, it))
                        }
                    }
                },
                trailingContent = entry.resultId?.takeIf { entry.state == RecoveryState.COMPLETED }?.let { id ->
                    {
                        TextButton(onClick = {
                            actions.openTitle(id)
                        }) { Text(stringResource(MR.strings.recovery_action_open)) }
                    }
                },
            )
        }
        item {
            TextButton(onClick = actions.startAnother, modifier = Modifier.padding(16.dp)) {
                Text(stringResource(MR.strings.recovery_action_start_another))
            }
        }
    }
}

@Composable
private fun ConfirmApplyDialog(readyCount: Int, keepOriginal: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.recovery_confirm_title, readyCount)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        if (keepOriginal) MR.strings.recovery_confirm_keep else MR.strings.recovery_confirm_replace,
                    ),
                )
                Text(stringResource(MR.strings.recovery_confirm_transfer))
                Text(stringResource(MR.strings.recovery_confirm_limits))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(MR.strings.recovery_action_apply, readyCount)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

@Composable
private fun ManualSearchDialog(initialQuery: String, onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    var query by remember { mutableStateOf(initialQuery) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.recovery_action_search_manually)) },
        text = {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(MR.strings.recovery_search_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSearch(query.trim()) }, enabled = query.isNotBlank()) {
                Text(stringResource(MR.strings.action_search))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) } },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
    )
}

@Composable
private fun reasonText(reason: String): String? = when (reason) {
    RecoveryReviewReason.SELF_TARGET.name -> stringResource(MR.strings.recovery_reason_self_target)
    RecoveryReviewReason.SHARED_DESTINATION.name -> stringResource(MR.strings.recovery_reason_shared_destination)
    RecoveryReviewReason.DESTINATION_IS_ORIGINAL.name -> stringResource(
        MR.strings.recovery_reason_destination_is_original,
    )
    RecoveryReviewReason.TRACKER_CONFLICT.name -> stringResource(MR.strings.recovery_reason_tracker_conflict)
    RecoveryReviewReason.MAPPING_CHANGED.name -> stringResource(MR.strings.recovery_reason_mapping_changed)
    RecoveryReviewReason.SEASONS_CANDIDATE.name -> stringResource(MR.strings.recovery_seasons_candidate)
    else -> reason
}

private fun RecoveryState.label() = when (this) {
    RecoveryState.SELECTED -> MR.strings.recovery_state_selected
    RecoveryState.SEARCHING -> MR.strings.recovery_state_searching
    RecoveryState.NEEDS_REVIEW -> MR.strings.recovery_state_needs_review
    RecoveryState.MISSING -> MR.strings.recovery_state_missing
    RecoveryState.READY -> MR.strings.recovery_state_ready
    RecoveryState.APPLYING -> MR.strings.recovery_state_applying
    RecoveryState.INTERRUPTED -> MR.strings.recovery_state_interrupted
    RecoveryState.COMPLETED -> MR.strings.recovery_state_completed
    RecoveryState.FAILED -> MR.strings.recovery_state_failed
    RecoveryState.SKIPPED -> MR.strings.recovery_state_skipped
    RecoveryState.UNSUPPORTED -> MR.strings.recovery_state_unsupported
}

private val previewActions = RecoveryActions(
    navigateUp = {}, toggleOriginal = {}, toggleSource = {}, setKeepOriginal = {}, findReplacements = {},
    choose = { _, _ ->
    }, approve = {}, skip = {}, reopen = {}, searchAgain = { _, _ -> }, searchRemaining = {}, apply = {},
    cancel = {}, retryFailed = {}, reviewUnresolved = {}, startAnother = {}, openTitle = {}, migrateAlone = {},
)

private fun previewEntry(
    id: Long,
    state: RecoveryState,
    candidates: List<RecoveryCandidate> = emptyList(),
) = RecoveryEntry(
    id = id, sessionId = 1, originalId = id, selected = true, state = state, searchQuery = "Title $id",
    candidates = candidates, searchErrors = emptyMap(), choice = null, pairs = emptyList(), resultId = null,
    error = null, runToken = null,
)

@PreviewLightDark
@Composable
private fun RecoverySelectPreview() {
    TachiyomiPreviewTheme {
        RecoveryScreenContent(
            state = RecoveryScreenModel.State(
                step = Step.Select,
                originals = listOf(
                    RecoveryOriginal(1, "Title 1", null, 1, seasons = false),
                    RecoveryOriginal(2, "Title 2", null, 1, seasons = true),
                ),
                selectedOriginals = setOf(1, 2),
                sources = listOf(RecoverySourceOption(20, "Source A", "en")),
                selectedSources = setOf(20),
            ),
            media = RecoveryMedia.ANIME,
            failedSourceName = "Old source",
            actions = previewActions,
        )
    }
}

@PreviewLightDark
@Composable
private fun RecoveryReviewPreview() {
    TachiyomiPreviewTheme {
        RecoveryScreenContent(
            state = RecoveryScreenModel.State(
                step = Step.Review,
                originals = listOf(RecoveryOriginal(1, "Title 1", null, 1, seasons = false)),
                sources = listOf(RecoverySourceOption(20, "Source A", "en")),
                entries = listOf(
                    previewEntry(1, RecoveryState.NEEDS_REVIEW, listOf(RecoveryCandidate(20, "/m/1", "Title 1"))),
                    previewEntry(2, RecoveryState.UNSUPPORTED),
                ),
            ),
            media = RecoveryMedia.MANGA,
            failedSourceName = null,
            actions = previewActions,
        )
    }
}
