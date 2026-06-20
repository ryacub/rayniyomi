package eu.kanade.presentation.track.anime

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
import eu.kanade.presentation.track.TrackInfoItem
import eu.kanade.presentation.track.TrackInfoItemEmpty
import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackItem
import eu.kanade.tachiyomi.util.lang.toLocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AnimeTrackInfoDialogHome(
    trackItems: List<AnimeTrackItem>,
    dateFormat: DateTimeFormatter,
    onStatusClick: (AnimeTrackItem) -> Unit,
    onEpisodeClick: (AnimeTrackItem) -> Unit,
    onScoreClick: (AnimeTrackItem) -> Unit,
    onStartDateEdit: (AnimeTrackItem) -> Unit,
    onEndDateEdit: (AnimeTrackItem) -> Unit,
    onNewSearch: (AnimeTrackItem) -> Unit,
    onOpenInBrowser: (AnimeTrackItem) -> Unit,
    onRemoved: (AnimeTrackItem) -> Unit,
    onCopyLink: (AnimeTrackItem) -> Unit,
    onTogglePrivate: (AnimeTrackItem) -> Unit,
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
            val track = item.track
            if (track != null) {
                val supportsScoring = item.tracker.animeService.getScoreList().isNotEmpty()
                val supportsReadingDates = item.tracker.supportsReadingDates
                val supportsPrivate = item.tracker.supportsPrivateTracking
                TrackInfoItem(
                    title = track.title,
                    tracker = item.tracker,
                    status = (item.tracker as? AnimeTracker)?.getStatusForAnime(track.status),
                    onStatusClick = { onStatusClick(item) },
                    progress = "${track.lastEpisodeSeen.toInt()}".let {
                        val totalEpisodes = track.totalEpisodes
                        if (totalEpisodes > 0) {
                            // Add known total episode count
                            "$it / $totalEpisodes"
                        } else {
                            it
                        }
                    },
                    onProgressClick = { onEpisodeClick(item) },
                    score = item.tracker.animeService.displayScore(track)
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
            } else {
                TrackInfoItemEmpty(
                    tracker = item.tracker,
                    onNewSearch = { onNewSearch(item) },
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun TrackInfoDialogHomePreviews(
    @PreviewParameter(AnimeTrackInfoDialogHomePreviewProvider::class)
    content: @Composable () -> Unit,
) {
    TachiyomiPreviewTheme { content() }
}
