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
import androidx.compose.material3.SelectableDates
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
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.track.TrackDateSelector
import eu.kanade.presentation.track.TrackInfoDialogHome
import eu.kanade.presentation.track.TrackItemSelector
import eu.kanade.presentation.track.TrackScoreSelector
import eu.kanade.presentation.track.TrackStatusSelector
import eu.kanade.presentation.track.TrackerSearch
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.StateViewModel
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.util.lang.convertEpochMillisZone
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.ImmutableList
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

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
                try {
                    tracking.registerEnhanced(item.tracker, entryId)
                } catch (e: Exception) {
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

internal data class TrackStatusSelectorScreen(
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
        val state by screenModel.state.collectAsStateWithLifecycle()
        TrackStatusSelector(
            selection = state.selection,
            onSelectionChange = screenModel::setSelection,
            selections = remember { screenModel.getSelections() },
            onConfirm = {
                screenModel.setStatus()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
        )
    }

    @AssistedInject
    internal class Model(
        @Assisted private val track: EntryTrack,
        @Assisted private val tracker: Tracker,
    ) : StateViewModel<Model.State>(State(track.status)) {

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        fun interface Factory : ManualViewModelAssistedFactory {
            fun create(
                @Assisted track: EntryTrack,
                @Assisted tracker: Tracker,
            ): Model
        }

        fun getSelections(): Map<Long, StringResource?> {
            return tracker.statusList(track).associateWith { tracker.statusName(track, it) }
        }

        fun setSelection(selection: Long) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setStatus() {
            viewModelScope.launchNonCancellable {
                tracker.setRemoteStatus(track, state.value.selection)
            }
        }

        @Immutable
        data class State(
            val selection: Long,
        )
    }
}

internal data class TrackProgressSelectorScreen(
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
        val state by screenModel.state.collectAsStateWithLifecycle()

        TrackItemSelector(
            selection = state.selection,
            onSelectionChange = screenModel::setSelection,
            range = remember { screenModel.getRange() },
            onConfirm = {
                screenModel.setProgress()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
            isManga = track is EntryTrack.Manga,
        )
    }

    @AssistedInject
    internal class Model(
        @Assisted private val track: EntryTrack,
        @Assisted private val tracker: Tracker,
    ) : StateViewModel<Model.State>(State(track.progress.toInt())) {

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        fun interface Factory : ManualViewModelAssistedFactory {
            fun create(
                @Assisted track: EntryTrack,
                @Assisted tracker: Tracker,
            ): Model
        }

        fun getRange(): Iterable<Int> {
            val endRange = if (track.total > 0) {
                track.total
            } else {
                10000
            }
            return 0..endRange.toInt()
        }

        fun setSelection(selection: Int) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setProgress() {
            viewModelScope.launchNonCancellable {
                tracker.setRemoteProgress(track, state.value.selection)
            }
        }

        @Immutable
        data class State(
            val selection: Int,
        )
    }
}

internal data class TrackScoreSelectorScreen(
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
        val state by screenModel.state.collectAsStateWithLifecycle()

        TrackScoreSelector(
            selection = state.selection,
            onSelectionChange = screenModel::setSelection,
            selections = remember { screenModel.getSelections() },
            onConfirm = {
                screenModel.setScore()
                navigator.pop()
            },
            onDismissRequest = navigator::pop,
        )
    }

    @AssistedInject
    internal class Model(
        @Assisted private val track: EntryTrack,
        @Assisted private val tracker: Tracker,
    ) : StateViewModel<Model.State>(State(tracker.displayScore(track))) {

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        fun interface Factory : ManualViewModelAssistedFactory {
            fun create(
                @Assisted track: EntryTrack,
                @Assisted tracker: Tracker,
            ): Model
        }

        fun getSelections(): ImmutableList<String> {
            return tracker.scoreList(track)
        }

        fun setSelection(selection: String) {
            mutableState.update { it.copy(selection = selection) }
        }

        fun setScore() {
            viewModelScope.launchNonCancellable {
                tracker.setRemoteScore(track, state.value.selection)
            }
        }

        @Immutable
        data class State(
            val selection: String,
        )
    }
}

internal data class TrackDateSelectorScreen(
    private val track: EntryTrack,
    private val serviceId: Long,
    private val start: Boolean,
) : Screen() {

    @Transient
    private val selectableDates = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
            val dateToCheck = Instant.ofEpochMilli(utcTimeMillis)
                .atZone(ZoneOffset.systemDefault())
                .toLocalDate()

            if (dateToCheck > LocalDate.now()) {
                // Disallow future dates
                return false
            }

            return if (start && track.finishDate > 0) {
                // Disallow start date to be set later than finish date
                val dateFinished = Instant.ofEpochMilli(track.finishDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                dateToCheck <= dateFinished
            } else if (!start && track.startDate > 0) {
                // Disallow end date to be set earlier than start date
                val dateStarted = Instant.ofEpochMilli(track.startDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                dateToCheck >= dateStarted
            } else {
                // Nothing set before
                true
            }
        }

        override fun isSelectableYear(year: Int): Boolean {
            if (year > LocalDate.now().year) {
                // Disallow future dates
                return false
            }

            return if (start && track.finishDate > 0) {
                // Disallow start date to be set later than finish date
                val dateFinished = Instant.ofEpochMilli(track.finishDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .year
                year <= dateFinished
            } else if (!start && track.startDate > 0) {
                // Disallow end date to be set earlier than start date
                val dateStarted = Instant.ofEpochMilli(track.startDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .year
                year >= dateStarted
            } else {
                // Nothing set before
                true
            }
        }
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = assistedMetroViewModel<Model, Model.Factory> {
            create(
                track = track,
                tracker = checkNotNull(appGraph.trackerManager.get(serviceId)) {
                    "Missing tracker for id: $serviceId"
                },
                start = start,
            )
        }

        val canRemove = if (start) {
            track.startDate > 0
        } else {
            track.finishDate > 0
        }
        TrackDateSelector(
            title = if (start) {
                stringResource(MR.strings.track_started_reading_date)
            } else {
                stringResource(MR.strings.track_finished_reading_date)
            },
            initialSelectedDateMillis = screenModel.initialSelection,
            selectableDates = selectableDates,
            onConfirm = {
                screenModel.setDate(it)
                navigator.pop()
            },
            onRemove = { screenModel.confirmRemoveDate(navigator) }.takeIf { canRemove },
            onDismissRequest = navigator::pop,
        )
    }

    @AssistedInject
    internal class Model(
        @Assisted private val track: EntryTrack,
        @Assisted private val tracker: Tracker,
        @Assisted private val start: Boolean,
    ) : ViewModel() {

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        fun interface Factory : ManualViewModelAssistedFactory {
            fun create(
                @Assisted track: EntryTrack,
                @Assisted tracker: Tracker,
                @Assisted start: Boolean,
            ): Model
        }

        // In UTC
        val initialSelection: Long
            get() {
                val millis =
                    (if (start) track.startDate else track.finishDate)
                        .takeIf { it != 0L }
                        ?: Instant.now().toEpochMilli()
                return millis.convertEpochMillisZone(ZoneOffset.systemDefault(), ZoneOffset.UTC)
            }

        // In UTC
        fun setDate(millis: Long) {
            // Convert to local time
            val localMillis =
                millis.convertEpochMillisZone(ZoneOffset.UTC, ZoneOffset.systemDefault())
            viewModelScope.launchNonCancellable {
                if (start) {
                    tracker.setRemoteStartDate(track, localMillis)
                } else {
                    tracker.setRemoteFinishDate(track, localMillis)
                }
            }
        }

        fun confirmRemoveDate(navigator: Navigator) {
            navigator.push(TrackDateRemoverScreen(track, tracker.id, start))
        }
    }
}

internal data class TrackDateRemoverScreen(
    private val track: EntryTrack,
    private val serviceId: Long,
    private val start: Boolean,
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
                start = start,
            )
        }
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
                    text = stringResource(MR.strings.track_remove_date_conf_title),
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                val serviceName = screenModel.getName()
                Text(
                    text = if (start) {
                        stringResource(MR.strings.track_remove_start_date_conf_text, serviceName)
                    } else {
                        stringResource(MR.strings.track_remove_finish_date_conf_text, serviceName)
                    },
                )
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
                            screenModel.removeDate()
                            navigator.popUntil { it is TrackInfoDialogHomeScreen }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.action_remove))
                    }
                }
            },
        )
    }

    @AssistedInject
    internal class Model(
        @Assisted private val track: EntryTrack,
        @Assisted private val tracker: Tracker,
        @Assisted private val start: Boolean,
    ) : ViewModel() {

        @AssistedFactory
        @ManualViewModelAssistedFactoryKey
        @ContributesIntoMap(AppScope::class)
        fun interface Factory : ManualViewModelAssistedFactory {
            fun create(
                @Assisted track: EntryTrack,
                @Assisted tracker: Tracker,
                @Assisted start: Boolean,
            ): Model
        }

        fun getName() = tracker.name

        fun removeDate() {
            viewModelScope.launchNonCancellable {
                if (start) {
                    tracker.setRemoteStartDate(track, 0)
                } else {
                    tracker.setRemoteFinishDate(track, 0)
                }
            }
        }
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
        TrackerSearch(
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
            viewModelScope.launchNonCancellable { trackings.of(track).unregister(track.entryId, serviceId) }
        }
    }
}
