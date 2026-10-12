package eu.kanade.presentation.track

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.ui.entries.track.EntryTrackInfoItem
import eu.kanade.tachiyomi.ui.entries.track.displayScore
import eu.kanade.tachiyomi.ui.entries.track.scoreList
import eu.kanade.tachiyomi.ui.entries.track.statusName
import eu.kanade.tachiyomi.util.lang.toLocalDate
import java.time.format.DateTimeFormatter

@Composable
fun TrackInfoDialogHome(
    trackItems: List<EntryTrackInfoItem>,
    dateFormat: DateTimeFormatter,
    onStatusClick: (EntryTrackInfoItem.Tracked) -> Unit,
    onProgressClick: (EntryTrackInfoItem.Tracked) -> Unit,
    onScoreClick: (EntryTrackInfoItem.Tracked) -> Unit,
    onStartDateEdit: (EntryTrackInfoItem.Tracked) -> Unit,
    onEndDateEdit: (EntryTrackInfoItem.Tracked) -> Unit,
    onNewSearch: (EntryTrackInfoItem) -> Unit,
    onOpenInBrowser: (EntryTrackInfoItem.Tracked) -> Unit,
    onRemoved: (EntryTrackInfoItem.Tracked) -> Unit,
    onCopyLink: (EntryTrackInfoItem.Tracked) -> Unit,
    onTogglePrivate: (EntryTrackInfoItem.Tracked) -> Unit,
) {
    Column(
        modifier = Modifier
            .animateContentSize()
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        trackItems.forEach { item ->
            when (item) {
                is EntryTrackInfoItem.Tracked -> {
                    val track = item.track
                    val supportsScoring = item.tracker.scoreList(track).isNotEmpty()
                    val supportsReadingDates = item.tracker.supportsReadingDates
                    val supportsPrivate = item.tracker.supportsPrivateTracking
                    TrackInfoItem(
                        title = track.title,
                        tracker = item.tracker,
                        status = item.tracker.statusName(track, track.status),
                        onStatusClick = { onStatusClick(item) },
                        progress = "${track.progress.toInt()}".let {
                            if (track.total > 0) {
                                "$it / ${track.total}"
                            } else {
                                it
                            }
                        },
                        onProgressClick = { onProgressClick(item) },
                        score = item.tracker.displayScore(track)
                            .takeIf { supportsScoring && track.score != 0.0 },
                        onScoreClick = { onScoreClick(item) }
                            .takeIf { supportsScoring },
                        startDate = remember(track.startDate) {
                            dateFormat.format(
                                track.startDate.toLocalDate(),
                            )
                        }
                            .takeIf { supportsReadingDates && track.startDate != 0L },
                        onStartDateClick = { onStartDateEdit(item) } // TODO
                            .takeIf { supportsReadingDates },
                        endDate = dateFormat.format(track.finishDate.toLocalDate())
                            .takeIf { supportsReadingDates && track.finishDate != 0L },
                        onEndDateClick = { onEndDateEdit(item) }
                            .takeIf { supportsReadingDates },
                        onNewSearch = { onNewSearch(item) },
                        onOpenInBrowser = { onOpenInBrowser(item) },
                        onRemoved = { onRemoved(item) },
                        onCopyLink = { onCopyLink(item) },
                        private = track.private,
                        onTogglePrivate = { onTogglePrivate(item) }
                            .takeIf { supportsPrivate },
                    )
                }
                is EntryTrackInfoItem.Untracked -> {
                    TrackInfoItemEmpty(
                        tracker = item.tracker,
                        onNewSearch = { onNewSearch(item) },
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun TrackInfoDialogHomePreviews(
    @PreviewParameter(TrackInfoDialogHomePreviewProvider::class)
    content: @Composable () -> Unit,
) {
    TachiyomiPreviewTheme { content() }
}
