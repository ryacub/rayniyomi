package eu.kanade.presentation.track.anime

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.track.TrackSearch
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch

@Composable
fun AnimeTrackerSearch(
    state: TextFieldState,
    onDispatchQuery: () -> Unit,
    queryResult: Result<List<AnimeTrackSearch>>?,
    selected: AnimeTrackSearch?,
    onSelectedChange: (AnimeTrackSearch) -> Unit,
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
        title = AnimeTrackSearch::title,
        coverUrl = AnimeTrackSearch::cover_url,
        trackingUrl = AnimeTrackSearch::tracking_url,
        authors = AnimeTrackSearch::authors,
        artists = AnimeTrackSearch::artists,
        publishingType = AnimeTrackSearch::publishing_type,
        publishingStatus = AnimeTrackSearch::publishing_status,
        startDate = AnimeTrackSearch::start_date,
        summary = AnimeTrackSearch::summary,
        score = AnimeTrackSearch::score,
    )
}

@PreviewLightDark
@Composable
private fun TrackerSearchPreviews(
    @PreviewParameter(AnimeTrackerSearchPreviewProvider::class)
    content: @Composable () -> Unit,
) {
    TachiyomiPreviewTheme {
        Surface {
            content()
        }
    }
}
