package eu.kanade.tachiyomi.ui.download.anime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun AnimeDownloadQueueScreen(
    contentPadding: PaddingValues,
    screenModel: AnimeDownloadQueueScreenModel,
    downloadList: List<AnimeDownloadUiHeaderItem>,
    nestedScrollConnection: NestedScrollConnection,
) {
    Scaffold {
        if (downloadList.isEmpty()) {
            EmptyScreen(
                stringRes = MR.strings.information_no_downloads,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        val lazyListState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(
            lazyListState,
            contentPadding,
        ) { from, to ->
            val fromHeaderIndex = findHeaderIndex(downloadList, from.index)
            val toHeaderIndex = findHeaderIndex(downloadList, to.index)
            if (fromHeaderIndex == toHeaderIndex) {
                val reorderedDownloads = computeReorderedDownloads(
                    downloadList,
                    from.index,
                    to.index,
                )
                screenModel.reorder(reorderedDownloads.mapNotNull { it.episode.id })
            }
        }

        Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
            LazyColumn(
                state = lazyListState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    items = downloadList,
                    key = { header -> header.source.id },
                ) { header ->
                    ReorderableItem(
                        reorderState,
                        header.source.id,
                    ) { isDragging ->
                        AnimeDownloadQueueHeader(
                            header = header,
                            isDragging = isDragging,
                            onDragStart = { screenModel.collapseAll() },
                            onDragEnd = { screenModel.expandHeader(header.source) },
                            onToggleExpanded = { screenModel.toggleExpanded(header.source) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    AnimatedVisibility(visible = header.isExpanded) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            header.downloads.forEach { item ->
                                key(item.download.episode.url) {
                                    AnimeDownloadQueueItem(
                                        item = item,
                                        screenModel = screenModel,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.AnimeDownloadQueueHeader(
    header: AnimeDownloadUiHeaderItem,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.DragIndicator,
            contentDescription = stringResource(MR.strings.action_sort),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .draggableHandle(
                    onDragStarted = { onDragStart() },
                    onDragStopped = onDragEnd,
                ),
        )

        Text(
            text = "${header.source.name} (${header.downloads.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )

        IconButton(
            onClick = onToggleExpanded,
            modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
            Icon(
                imageVector = if (header.isExpanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = if (header.isExpanded) {
                    stringResource(MR.strings.action_collapse)
                } else {
                    stringResource(MR.strings.action_expand)
                },
            )
        }
    }
}

@Composable
private fun AnimeDownloadQueueItem(
    item: AnimeDownloadUiItem,
    screenModel: AnimeDownloadQueueScreenModel,
    modifier: Modifier = Modifier,
) {
    val progress by remember { derivedStateOf { item.progress } }
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.download.episode.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(MR.strings.action_settings),
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_move_to_top)) },
                        onClick = {
                            screenModel.moveToTop(item)
                            menuExpanded = false
                        },
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_move_to_bottom)) },
                        onClick = {
                            screenModel.moveToBottom(item)
                            menuExpanded = false
                        },
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_move_to_top_all_for_series)) },
                        onClick = {
                            screenModel.moveToTopSeries(item.download.anime.id)
                            menuExpanded = false
                        },
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_move_to_bottom_all_for_series)) },
                        onClick = {
                            screenModel.moveToBottomSeries(item.download.anime.id)
                            menuExpanded = false
                        },
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_cancel)) },
                        onClick = {
                            screenModel.cancelDownload(item)
                            menuExpanded = false
                        },
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_delete)) },
                        onClick = {
                            screenModel.cancelSeries(item.download.anime.id)
                            menuExpanded = false
                        },
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Preview
@Composable
private fun AnimeDownloadQueueScreenPreview() {
    EmptyScreen(
        stringRes = MR.strings.information_no_downloads,
    )
}

private fun findHeaderIndex(
    items: List<AnimeDownloadUiHeaderItem>,
    flatIndex: Int,
): Int {
    var currentIndex = 0
    items.forEachIndexed { headerIdx, header ->
        if (currentIndex == flatIndex) return headerIdx
        currentIndex++
        if (currentIndex + header.downloads.size > flatIndex) {
            return headerIdx
        }
        currentIndex += header.downloads.size
    }
    return -1
}

private fun computeReorderedDownloads(
    items: List<AnimeDownloadUiHeaderItem>,
    fromIndex: Int,
    toIndex: Int,
): List<AnimeDownload> {
    val flatList = mutableListOf<Pair<AnimeDownloadUiItem?, Boolean>>()
    items.forEach { header ->
        flatList.add(Pair(null, true))
        flatList.addAll(header.downloads.map { Pair(it, false) })
    }

    if (fromIndex < 0 || fromIndex >= flatList.size || toIndex < 0 || toIndex >= flatList.size) {
        return items.flatMap { it.downloads.map { item -> item.download } }
    }

    val reordered = flatList.toMutableList()
    val item = reordered.removeAt(fromIndex)
    reordered.add(toIndex, item)

    return reordered
        .mapNotNull { (uiItem, _) -> uiItem }
        .map { it.download }
}
