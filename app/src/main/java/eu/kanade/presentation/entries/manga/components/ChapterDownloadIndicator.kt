package eu.kanade.presentation.entries.manga.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import eu.kanade.presentation.components.ActiveDownloadMark
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.IndicatorSize
import eu.kanade.presentation.components.UpdatesDownloadMark
import eu.kanade.presentation.components.commonClickable
import eu.kanade.presentation.components.downloadCompletionScale
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

enum class ChapterDownloadAction {
    START,
    START_NOW,
    CANCEL,
    DELETE,
}

@Composable
fun ChapterDownloadIndicator(
    enabled: Boolean,
    downloadStateProvider: () -> MangaDownload.State,
    downloadProgressProvider: () -> Int,
    onClick: (ChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
    downloadPaused: Boolean = false,
    useTitleListStyle: Boolean = false,
) {
    val downloadState = downloadStateProvider()
    val completionScale = if (useTitleListStyle) {
        downloadCompletionScale(downloadState == MangaDownload.State.DOWNLOADED)
    } else {
        null
    }
    when (downloadState) {
        MangaDownload.State.NOT_DOWNLOADED -> NotDownloadedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
        MangaDownload.State.QUEUE, MangaDownload.State.DOWNLOADING -> DownloadingIndicator(
            enabled = enabled,
            modifier = modifier,
            queued = downloadState == MangaDownload.State.QUEUE,
            paused = downloadPaused,
            useTitleListStyle = useTitleListStyle,
            downloadProgressProvider = downloadProgressProvider,
            onClick = onClick,
        )
        MangaDownload.State.DOWNLOADED -> DownloadedIndicator(
            enabled = enabled,
            completionScale = { completionScale?.value ?: 1f },
            modifier = modifier,
            onClick = onClick,
        )
        MangaDownload.State.ERROR -> ErrorIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = onClick,
        )
    }
}

@Composable
private fun NotDownloadedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterDownloadAction) -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterDownloadAction.START_NOW) },
                onClick = { onClick(ChapterDownloadAction.START) },
            )
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_download_item_24dp),
            contentDescription = stringResource(MR.strings.manga_download),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadingIndicator(
    enabled: Boolean,
    queued: Boolean,
    paused: Boolean,
    useTitleListStyle: Boolean,
    downloadProgressProvider: () -> Int,
    onClick: (ChapterDownloadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterDownloadAction.CANCEL) },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        val progress = downloadProgressProvider()
        if (useTitleListStyle) {
            ActiveDownloadMark(queued, paused, progress)
        } else {
            UpdatesDownloadMark(queued, progress)
        }
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_start_downloading_now)) },
                onClick = {
                    onClick(ChapterDownloadAction.START_NOW)
                    isMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_cancel)) },
                onClick = {
                    onClick(ChapterDownloadAction.CANCEL)
                    isMenuExpanded = false
                },
            )
        }
    }
}

@Composable
private fun DownloadedIndicator(
    enabled: Boolean,
    completionScale: () -> Float,
    modifier: Modifier = Modifier,
    onClick: (ChapterDownloadAction) -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { isMenuExpanded = true },
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = stringResource(MR.strings.label_downloaded),
            modifier = Modifier.size(IndicatorSize).graphicsLayer {
                val scale = completionScale()
                scaleX = scale
                scaleY = scale
            },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(MR.strings.action_delete)) },
                onClick = {
                    onClick(ChapterDownloadAction.DELETE)
                    isMenuExpanded = false
                },
            )
        }
    }
}

@Composable
private fun ErrorIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: (ChapterDownloadAction) -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onLongClick = { onClick(ChapterDownloadAction.START) },
                onClick = { onClick(ChapterDownloadAction.START) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = stringResource(AYMR.strings.download_error),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}
