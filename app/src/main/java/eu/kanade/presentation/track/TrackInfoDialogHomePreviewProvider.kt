package eu.kanade.presentation.track

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider

internal class TrackInfoDialogHomePreviewProvider :
    PreviewParameterProvider<@Composable () -> Unit> {

    private val trackItemWithoutTrack = previewTrackItemWithoutTrack()
    private val trackItemWithTrack = previewMangaTrackItemWithTrack()
    private val animeTrackItemWithTrack = previewAnimeTrackItemWithTrack()
    private val trackItemWithPrivateTrack = previewMangaTrackItemWithTrack(privateTracking = true)

    private val trackersWithAndWithoutTrack = @Composable {
        TrackInfoDialogHome(
            trackItems = listOf(
                trackItemWithoutTrack,
                trackItemWithTrack,
                animeTrackItemWithTrack,
            ),
            dateFormat = previewTrackDateFormat(),
            onStatusClick = {},
            onProgressClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    private val noTrackers = @Composable {
        TrackInfoDialogHome(
            trackItems = listOf(),
            dateFormat = previewTrackDateFormat(),
            onStatusClick = {},
            onProgressClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    private val trackerWithPrivateTracking = @Composable {
        TrackInfoDialogHome(
            trackItems = listOf(trackItemWithPrivateTrack),
            dateFormat = previewTrackDateFormat(),
            onStatusClick = {},
            onProgressClick = {},
            onScoreClick = {},
            onStartDateEdit = {},
            onEndDateEdit = {},
            onNewSearch = {},
            onOpenInBrowser = {},
            onRemoved = {},
            onCopyLink = {},
            onTogglePrivate = {},
        )
    }

    override val values: Sequence<@Composable () -> Unit>
        get() = sequenceOf(
            trackersWithAndWithoutTrack,
            noTrackers,
            trackerWithPrivateTracking,
        )
}
