package eu.kanade.presentation.track.anime

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.presentation.track.previewAnimeTrackSearches

internal class AnimeTrackerSearchPreviewProvider : PreviewParameterProvider<@Composable () -> Unit> {
    private val fullPageWithSecondSelected = @Composable {
        val items = previewAnimeTrackSearches().take(30).toList()
        AnimeTrackerSearch(
            state = TextFieldState(initialText = "search text"),
            onDispatchQuery = {},
            queryResult = Result.success(items),
            selected = items[1],
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val fullPageWithoutSelected = @Composable {
        AnimeTrackerSearch(
            state = TextFieldState(),
            onDispatchQuery = {},
            queryResult = Result.success(previewAnimeTrackSearches().take(30).toList()),
            selected = null,
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val loading = @Composable {
        AnimeTrackerSearch(
            state = TextFieldState(),
            onDispatchQuery = {},
            queryResult = null,
            selected = null,
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = false,
        )
    }
    private val fullPageWithPrivateTracking = @Composable {
        val items = previewAnimeTrackSearches().take(30).toList()
        AnimeTrackerSearch(
            state = TextFieldState(initialText = "search text"),
            onDispatchQuery = {},
            queryResult = Result.success(items),
            selected = items[1],
            onSelectedChange = {},
            onConfirmSelection = {},
            onDismissRequest = {},
            supportsPrivateTracking = true,
        )
    }
    override val values: Sequence<@Composable () -> Unit> = sequenceOf(
        fullPageWithSecondSelected,
        fullPageWithoutSelected,
        loading,
        fullPageWithPrivateTracking,
    )
}
