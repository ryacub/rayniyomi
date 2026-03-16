package eu.kanade.tachiyomi.ui.library.duplicate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.interactor.MergeLibraryManga
import tachiyomi.domain.entries.manga.interactor.ScanLibraryDuplicates
import tachiyomi.domain.entries.manga.model.DuplicateCandidate
import tachiyomi.domain.entries.manga.model.DuplicateConfidence
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class DuplicateScanScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { DuplicateScanScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(AYMR.strings.pref_scan_library_duplicates),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(screenModel.snackbarHostState) },
        ) { contentPadding ->
            when (val s = state) {
                is DuplicateScanScreenModel.State.Loading -> LoadingScreen(Modifier.padding(contentPadding))
                is DuplicateScanScreenModel.State.Success -> {
                    if (s.duplicates.isEmpty()) {
                        EmptyScreen(
                            stringRes = MR.strings.information_no_entries_found,
                            modifier = Modifier.padding(contentPadding),
                        )
                    } else {
                        DuplicateScanContent(
                            duplicates = s.duplicates,
                            onMerge = screenModel::merge,
                            onDismiss = screenModel::dismiss,
                            contentPadding = contentPadding,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateScanContent(
    duplicates: ImmutableList<DuplicateCandidate>,
    onMerge: (DuplicateCandidate) -> Unit,
    onDismiss: (DuplicateCandidate) -> Unit,
    contentPadding: androidx.compose.foundation.layout.PaddingValues,
) {
    LazyColumn(contentPadding = contentPadding) {
        items(duplicates, key = { "${it.winner.id}-${it.loser.id}" }) { candidate ->
            DuplicateCandidateRow(
                candidate = candidate,
                onMerge = { onMerge(candidate) },
                onDismiss = { onDismiss(candidate) },
            )
        }
    }
}

@Composable
private fun DuplicateCandidateRow(
    candidate: DuplicateCandidate,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confidenceLabel = when (candidate.confidence) {
        DuplicateConfidence.TRACKER -> stringResource(AYMR.strings.duplicate_confidence_tracker)
        DuplicateConfidence.HIGH -> stringResource(AYMR.strings.duplicate_confidence_title)
        DuplicateConfidence.MEDIUM -> stringResource(AYMR.strings.duplicate_confidence_normalized)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = candidate.winner.title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = candidate.loser.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = confidenceLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Button(
                onClick = onMerge,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(AYMR.strings.action_merge_entries))
            }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(MR.strings.action_cancel))
            }
        }
    }
}

class DuplicateScanScreenModel(
    private val scanLibraryDuplicates: ScanLibraryDuplicates = Injekt.get(),
    private val mergeLibraryManga: MergeLibraryManga = Injekt.get(),
) : StateScreenModel<DuplicateScanScreenModel.State>(State.Loading) {

    val snackbarHostState = SnackbarHostState()

    init {
        screenModelScope.launchIO {
            val duplicates = scanLibraryDuplicates.await()
            mutableState.value = State.Success(duplicates.toImmutableList())
        }
    }

    fun merge(candidate: DuplicateCandidate) {
        screenModelScope.launchIO {
            try {
                mergeLibraryManga.await(keepId = candidate.winner.id, deleteId = candidate.loser.id)
                val current = state.value
                if (current is State.Success) {
                    mutableState.value = State.Success(
                        current.duplicates
                            .filter { it.winner.id != candidate.loser.id && it.loser.id != candidate.loser.id }
                            .toImmutableList(),
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to merge library entries" }
                snackbarHostState.showSnackbar("Failed to merge entries")
            }
        }
    }

    fun dismiss(candidate: DuplicateCandidate) {
        val current = state.value
        if (current is State.Success) {
            mutableState.value = State.Success(
                current.duplicates
                    .filter { it !== candidate }
                    .toImmutableList(),
            )
        }
    }

    sealed interface State {
        data object Loading : State
        data class Success(val duplicates: ImmutableList<DuplicateCandidate>) : State
    }
}
