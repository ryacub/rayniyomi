package eu.kanade.presentation.track.anime

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.presentation.track.previewAnimeTrackItemWithTrack
import eu.kanade.presentation.track.previewAnimeTrackItemWithoutTrack
import eu.kanade.presentation.track.previewTrackDateFormat

internal class AnimeTrackInfoDialogHomePreviewProvider :
    PreviewParameterProvider<@Composable () -> Unit> {

    private val trackItemWithoutTrack = previewAnimeTrackItemWithoutTrack()
    private val trackItemWithTrack = previewAnimeTrackItemWithTrack()
    private val trackItemWithPrivateTrack = previewAnimeTrackItemWithTrack(privateTracking = true)

    private val trackersWithAndWithoutTrack = @Composable {
        AnimeTrackInfoDialogHome(
            trackItems = listOf(
                trackItemWithoutTrack,
                trackItemWithTrack,
            ),
            dateFormat = previewTrackDateFormat(),
            onStatusClick = {},
            onEpisodeClick = {},
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
        AnimeTrackInfoDialogHome(
            trackItems = listOf(),
            dateFormat = previewTrackDateFormat(),
            onStatusClick = {},
            onEpisodeClick = {},
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
        AnimeTrackInfoDialogHome(
            trackItems = listOf(trackItemWithPrivateTrack),
            dateFormat = previewTrackDateFormat(),
            onStatusClick = {},
            onEpisodeClick = {},
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
