package eu.kanade.presentation.track.manga

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import eu.kanade.presentation.track.previewMangaTrackItemWithTrack
import eu.kanade.presentation.track.previewMangaTrackItemWithoutTrack
import eu.kanade.presentation.track.previewTrackDateFormat

internal class MangaTrackInfoDialogHomePreviewProvider :
    PreviewParameterProvider<@Composable () -> Unit> {

    private val trackItemWithoutTrack = previewMangaTrackItemWithoutTrack()
    private val trackItemWithTrack = previewMangaTrackItemWithTrack()
    private val trackItemWithPrivateTrack = previewMangaTrackItemWithTrack(privateTracking = true)

    private val trackersWithAndWithoutTrack = @Composable {
        MangaTrackInfoDialogHome(
            trackItems = listOf(
                trackItemWithoutTrack,
                trackItemWithTrack,
            ),
            dateFormat = previewTrackDateFormat(),
            onStatusClick = {},
            onChapterClick = {},
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
        MangaTrackInfoDialogHome(
            trackItems = listOf(),
            dateFormat = previewTrackDateFormat(),
            onStatusClick = {},
            onChapterClick = {},
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
        MangaTrackInfoDialogHome(
            trackItems = listOf(trackItemWithPrivateTrack),
            dateFormat = previewTrackDateFormat(),
            onStatusClick = {},
            onChapterClick = {},
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
