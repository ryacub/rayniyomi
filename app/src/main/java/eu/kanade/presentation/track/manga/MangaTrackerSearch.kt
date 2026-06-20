package eu.kanade.presentation.track.manga

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.track.TrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch

@Composable
fun MangaTrackerSearch(
    state: TextFieldState,
    onDispatchQuery: () -> Unit,
    queryResult: Result<List<MangaTrackSearch>>?,
    selected: MangaTrackSearch?,
    onSelectedChange: (MangaTrackSearch) -> Unit,
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
        title = MangaTrackSearch::title,
        coverUrl = MangaTrackSearch::cover_url,
        trackingUrl = MangaTrackSearch::tracking_url,
        authors = MangaTrackSearch::authors,
        artists = MangaTrackSearch::artists,
        publishingType = MangaTrackSearch::publishing_type,
        publishingStatus = MangaTrackSearch::publishing_status,
        startDate = MangaTrackSearch::start_date,
        summary = MangaTrackSearch::summary,
        score = MangaTrackSearch::score,
    )
}

@PreviewLightDark
@Composable
private fun TrackerSearchPreviews(
    @PreviewParameter(MangaTrackerSearchPreviewProvider::class)
    content: @Composable () -> Unit,
) {
    TachiyomiPreviewTheme {
        Surface {
            content()
        }
    }
}
