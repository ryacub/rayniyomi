package eu.kanade.tachiyomi.ui.entries.track

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.track.TrackInfoDialogHome
import eu.kanade.presentation.track.TrackSearch
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.StateViewModel
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.AlertDialogContent
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

data class TrackInfoDialogHomeScreen(
    private val entryId: Long,
    private val entryTitle: String,
    private val sourceId: Long,
    private val type: EntryType,
) : Screen() {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = assistedMetroViewModel<Model, Model.Factory> { create(entryId, sourceId, type) }

        val dateFormat = remember {
            UiPreferences.dateFormat(
                appGraph.uiPreferences.dateFormat().get(),
            )
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        TrackInfoDialogHome(
            trackItems = state.trackItems,
            dateFormat = dateFormat,
            onStatusClick = {
                navigator.push(
                    TrackStatusSelectorScreen(
                        track = it.track,
                        serviceId = it.tracker.id,
                    ),
                )
            },
            onProgressClick = {
                navigator.push(
                    TrackProgressSelectorScreen(
                        track = it.track,
                        serviceId = it.tracker.id,
                    ),
                )
            },
            onScoreClick = {
                navigator.push(
                    TrackScoreSelectorScreen(
                        track = it.track,
                        serviceId = it.tracker.id,
                    ),
                )
            },
            onStartDateEdit = {
                navigator.push(
                    TrackDateSelectorScreen(
                        track = it.track,
                        serviceId = it.tracker.id,
                        start = true,
                    ),
                )
            },
            onEndDateEdit = {
                navigator.push(
                    TrackDateSelectorScreen(
                        track = it.track,
                        serviceId = it.tracker.id,
                        start = false,
                    ),
                )
            },
            onNewSearch = {
                if (screenModel.isEnhanced(it.tracker)) {
                    screenModel.registerEnhancedTracking(it)
                } else {
                    val track = (it as? EntryTrackInfoItem.Tracked)?.track
                    navigator.push(
                        TrackServiceSearchScreen(
                            entryId = entryId,
                            type = type,
                            initialQuery = track?.title ?: entryTitle,
                            currentUrl = track?.remoteUrl,
                            serviceId = it.tracker.id,
                        ),
                    )
                }
            },
            onOpenInBrowser = { openTrackerInBrowser(context, it) },
            onRemoved = {
                navigator.push(
                    TrackerRemoveScreen(
                        track = it.track,
                        serviceId = it.tracker.id,
                    ),
                )
            },
            onCopyLink = { context.copyTrackerLink(it) },
            onTogglePrivate = screenModel::togglePrivate,
        )
    }

    /**
     * Opens registered tracker url in browser
     */
    private fun openTrackerInBrowser(context: Context, trackItem: EntryTrackInfoItem.Tracked) {
        val url = trackItem.track.remoteUrl
        if (url.isNotBlank()) {
            context.openInBrowser(url)
        }
    }

    private fun Context.copyTrackerLink(trackItem: EntryTrackInfoItem.Tracked) {
        val url = trackItem.track.remoteUrl
        if (url.isNotBlank()) {
            copyToClipboard(url, url)
        }
    }

    @AssistedInject
    internal class Model(
        @Assisted private val entryId: Long,
        @Assisted private val sourceId: Long,
        @Assisted type: EntryType,
        trackings: EntryTrackings,
        private val application: Application,
        private val trackerManager: TrackerManager,
    ) : StateViewModel<Model.State>(State()) {

        private val tracking = trackings.of(type)

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        fun interface Factory : ManualViewModelAssistedFactory {
            fun create(
                @Assisted entryId: Long,
                @Assisted sourceId: Long,
                @Assisted type: EntryType,
            ): Model
        }

        init {
            viewModelScope.launch {
                refreshTrackers()
            }

            viewModelScope.launch {
                tracking.subscribeTracks(entryId)
                    .catch { logcat(LogPriority.ERROR, it) }
                    .distinctUntilChanged()
                    .map { it.mapToTrackItem() }
                    .collectLatest { trackItems ->
                        mutableState.update {
                            it.copy(
                                trackItems = trackItems,
                            )
                        }
                    }
            }
        }

        fun isEnhanced(tracker: Tracker) = tracking.isEnhanced(tracker)

        fun registerEnhancedTracking(item: EntryTrackInfoItem) {
            viewModelScope.launchNonCancellable {
                if (tracking.registerEnhanced(item.tracker, entryId) == EnhancedRegistration.NO_MATCH) {
                    withUIContext { application.toast(MR.strings.error_no_match) }
                }
            }
        }

        private suspend fun refreshTrackers() {
            val context = application

            tracking.refreshTracks(entryId)
                .forEach { (track, e) ->
                    val tracker = track ?: return@forEach
                    logcat(LogPriority.ERROR, e) {
                        "Failed to refresh track data entryId=$entryId for service ${tracker.name}"
                    }
                    withUIContext {
                        context.toast(
                            context.stringResource(
                                MR.strings.track_error,
                                tracker.name,
                                e.message ?: "",
                            ),
                        )
                    }
                }
        }

        fun togglePrivate(item: EntryTrackInfoItem.Tracked) {
            viewModelScope.launchNonCancellable {
                item.tracker.setRemotePrivate(item.track, !item.track.private)
            }
        }

        private fun List<EntryTrack>.mapToTrackItem(): List<EntryTrackInfoItem> {
            val loggedInTrackers = trackerManager.loggedInTrackers().filter(tracking::supports)
            return loggedInTrackers
                .map { service ->
                    find { it.trackerId == service.id }
                        ?.let { EntryTrackInfoItem.Tracked(it, service) }
                        ?: EntryTrackInfoItem.Untracked(service)
                }
                .filter { tracking.accepts(it.tracker, sourceId) }
        }

        @Immutable
        data class State(
            val trackItems: List<EntryTrackInfoItem> = emptyList(),
        )
    }
}

data class TrackServiceSearchScreen(
    private val entryId: Long,
    private val type: EntryType,
    private val initialQuery: String,
    private val currentUrl: String?,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = assistedMetroViewModel<Model, Model.Factory> {
            create(
                entryId = entryId,
                type = type,
                currentUrl = currentUrl,
                initialQuery = initialQuery,
                tracker = checkNotNull(appGraph.trackerManager.get(serviceId)) {
                    "Missing tracker for id: $serviceId"
                },
            )
        }

        val state by screenModel.state.collectAsStateWithLifecycle()

        val textFieldState = rememberTextFieldState(initialQuery)
        TrackSearch(
            state = textFieldState,
            onDispatchQuery = { screenModel.trackingSearch(textFieldState.text.toString()) },
            queryResult = state.queryResult,
            selected = state.selected,
            onSelectedChange = screenModel::updateSelection,
            onConfirmSelection = f@{ private: Boolean ->
                val selected = state.selected ?: return@f
                selected.private = private
                screenModel.registerTracking(selected)
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
            supportsPrivateTracking = screenModel.supportsPrivateTracking,
        )
    }

    @AssistedInject
    internal class Model(
        @Assisted private val entryId: Long,
        @Assisted type: EntryType,
        @Assisted private val currentUrl: String?,
        @Assisted private val initialQuery: String,
        @Assisted private val tracker: Tracker,
        trackings: EntryTrackings,
    ) : StateViewModel<Model.State>(State()) {

        private val tracking = trackings.of(type)

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        fun interface Factory : ManualViewModelAssistedFactory {
            fun create(
                @Assisted entryId: Long,
                @Assisted type: EntryType,
                @Assisted currentUrl: String?,
                @Assisted initialQuery: String,
                @Assisted tracker: Tracker,
            ): Model
        }

        val supportsPrivateTracking = tracker.supportsPrivateTracking

        init {
            // Run search on first launch
            if (initialQuery.isNotBlank()) {
                trackingSearch(initialQuery)
            }
        }

        fun trackingSearch(query: String) {
            viewModelScope.launch {
                // To show loading state
                mutableState.update { it.copy(queryResult = null, selected = null) }

                val result = withIOContext {
                    try {
                        val results = tracking.search(tracker, query)
                        Result.success(results)
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }
                }
                mutableState.update { oldState ->
                    oldState.copy(
                        queryResult = result,
                        selected = result.getOrNull()?.find { it.trackingUrl == currentUrl },
                    )
                }
            }
        }

        fun registerTracking(item: EntryTrackSearch) {
            viewModelScope.launchNonCancellable { tracker.register(item, entryId) }
        }

        fun updateSelection(selected: EntryTrackSearch) {
            mutableState.update { it.copy(selected = selected) }
        }

        @Immutable
        data class State(
            val queryResult: Result<List<EntryTrackSearch>>? = null,
            val selected: EntryTrackSearch? = null,
        )
    }
}

internal data class TrackerRemoveScreen(
    private val track: EntryTrack,
    private val serviceId: Long,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = assistedMetroViewModel<Model, Model.Factory> {
            create(
                track = track,
                tracker = checkNotNull(appGraph.trackerManager.get(serviceId)) {
                    "Missing tracker for id: $serviceId"
                },
            )
        }
        val serviceName = screenModel.getName()
        var removeRemoteTrack by remember { mutableStateOf(false) }
        AlertDialogContent(
            modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars),
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                )
            },
            title = {
                Text(
                    text = stringResource(MR.strings.track_delete_title, serviceName),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    Text(
                        text = stringResource(MR.strings.track_delete_text, serviceName),
                    )
                    if (screenModel.isDeletable()) {
                        LabeledCheckbox(
                            label = stringResource(MR.strings.track_delete_remote_text, serviceName),
                            checked = removeRemoteTrack,
                            onCheckedChange = { removeRemoteTrack = it },
                        )
                    }
                }
            },
            buttons = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(
                        MaterialTheme.padding.small,
                        Alignment.End,
                    ),
                ) {
                    TextButton(onClick = navigator::pop) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    FilledTonalButton(
                        onClick = {
                            screenModel.unregisterTracking(serviceId)
                            if (removeRemoteTrack) screenModel.deleteFromService()
                            navigator.pop()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
            },
        )
    }

    @AssistedInject
    internal class Model(
        @Assisted private val track: EntryTrack,
        @Assisted private val tracker: Tracker,
        private val trackings: EntryTrackings,
    ) : ViewModel() {

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        fun interface Factory : ManualViewModelAssistedFactory {
            fun create(
                @Assisted track: EntryTrack,
                @Assisted tracker: Tracker,
            ): Model
        }

        fun getName() = tracker.name

        fun isDeletable() = tracker.isDeletable(track)

        fun deleteFromService() {
            viewModelScope.launchNonCancellable {
                try {
                    tracker.deleteRemote(track)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to delete entry from service" }
                }
            }
        }

        fun unregisterTracking(serviceId: Long) {
            viewModelScope.launchNonCancellable { trackings.of(track.type).unregister(track.entryId, serviceId) }
        }
    }
}
