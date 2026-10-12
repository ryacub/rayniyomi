package eu.kanade.presentation.track

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.entries.track.EntryTrackSearch

@Composable
fun TrackerSearch(
    state: TextFieldState,
    onDispatchQuery: () -> Unit,
    queryResult: Result<List<EntryTrackSearch>>?,
    selected: EntryTrackSearch?,
    onSelectedChange: (EntryTrackSearch) -> Unit,
    onConfirmSelection: (private: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
    supportsPrivateTracking: Boolean,
) {
    TrackSearch(
        state = state,
        onDispatchQuery = onDispatchQuery,
        queryResult = queryResult,
        selected = selected,
        onSelectedChange = onSelectedChange,
        onConfirmSelection = onConfirmSelection,
        onDismissRequest = onDismissRequest,
        supportsPrivateTracking = supportsPrivateTracking,
        title = EntryTrackSearch::title,
        coverUrl = EntryTrackSearch::coverUrl,
        trackingUrl = EntryTrackSearch::trackingUrl,
        authors = EntryTrackSearch::authors,
        artists = EntryTrackSearch::artists,
        publishingType = EntryTrackSearch::publishingType,
        publishingStatus = EntryTrackSearch::publishingStatus,
        startDate = EntryTrackSearch::startDate,
        summary = EntryTrackSearch::summary,
        score = EntryTrackSearch::score,
    )
}

@PreviewLightDark
@Composable
private fun TrackerSearchPreviews(
    @PreviewParameter(TrackerSearchPreviewProvider::class)
    content: @Composable () -> Unit,
) {
    TachiyomiPreviewTheme {
        Surface {
            content()
        }
    }
}
