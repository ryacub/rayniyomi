package eu.kanade.tachiyomi.ui.download

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.data.download.model.DownloadRecovery
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun <I : DownloadQueueEntry> DownloadQueueScreen(
    contentPadding: PaddingValues,
    screenModel: DownloadQueueActions<I>,
    downloadList: List<DownloadQueueGroup<I>>,
    nestedScrollConnection: NestedScrollConnection,
) {
    Scaffold {
        val lazyListState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(
            lazyListState,
            contentPadding,
        ) { from, to ->
            val fromIndex = downloadList.indexOfFirst { it.sourceId == from.key }
            val toIndex = downloadList.indexOfFirst { it.sourceId == to.key }
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
                val reorderedGroups = downloadList.toMutableList()
                val movedGroup = reorderedGroups.removeAt(fromIndex)
                reorderedGroups.add(toIndex, movedGroup)
                screenModel.reorder(reorderedGroups.flatMap { group -> group.downloads.mapNotNull { it.downloadId } })
            }
        }

        Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
            if (downloadList.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.information_no_downloads,
                    modifier = Modifier.padding(contentPadding),
                )
            }
            LazyColumn(
                state = lazyListState,
                contentPadding = contentPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                downloadList.forEach { header ->
                    item(key = header.sourceId) {
                        ReorderableItem(
                            state = reorderState,
                            key = header.sourceId,
                            animateItemModifier = Modifier.animateItem(),
                        ) { isDragging ->
                            DownloadQueueHeader(
                                header = header,
                                isDragging = isDragging,
                                onDragStart = { screenModel.collapseAll() },
                                onDragEnd = { screenModel.expandHeader(header.sourceId) },
                                onToggleExpanded = { screenModel.toggleExpanded(header.sourceId) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (header.isExpanded) {
                        items(
                            items = header.downloads,
                            key = { item ->
                                item.downloadId?.let { "download-$it" } ?: "download-url-${header.sourceId}-${item.key}"
                            },
                        ) { item ->
                            DownloadQueueItem(
                                item = item,
                                screenModel = screenModel,
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.DownloadQueueHeader(
    header: DownloadQueueGroup<*>,
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
            text = "${header.sourceName} (${header.downloads.size})",
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
private fun <I : DownloadQueueEntry> DownloadQueueItem(
    item: I,
    screenModel: DownloadQueueActions<I>,
    modifier: Modifier = Modifier,
) {
    val progress = item.progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "Download progress",
    )
    val recovery by item.recovery.collectAsStateWithLifecycle()
    val displayStatus by item.displayStatus.collectAsStateWithLifecycle()
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
                text = item.name,
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
                    val waitingForConnection = displayStatus == DownloadDisplayStatus.WAITING_FOR_NETWORK ||
                        displayStatus == DownloadDisplayStatus.WAITING_FOR_WIFI
                    if (displayStatus == DownloadDisplayStatus.FAILED ||
                        (item.canRetryBlocked && waitingForConnection)
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(MR.strings.action_retry)) },
                            onClick = {
                                screenModel.retryDownload(item)
                                menuExpanded = false
                            },
                        )
                    }

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
                            screenModel.moveToTopSeries(item.entryId)
                            menuExpanded = false
                        },
                    )

                    DropdownMenuItem(
                        text = { Text(stringResource(MR.strings.action_move_to_bottom_all_for_series)) },
                        onClick = {
                            screenModel.moveToBottomSeries(item.entryId)
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
                            screenModel.cancelSeries(item.entryId)
                            menuExpanded = false
                        },
                    )
                }
            }
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
        val recoveryMessage = when (displayStatus) {
            DownloadDisplayStatus.WAITING_FOR_NETWORK -> MR.strings.download_notifier_no_network
            DownloadDisplayStatus.WAITING_FOR_WIFI -> MR.strings.download_notifier_text_only_wifi
            else -> when (recovery) {
                DownloadRecovery.RESUME -> MR.strings.download_recovery_resume
                DownloadRecovery.REPAIR -> MR.strings.download_recovery_repair
                DownloadRecovery.RESTART -> MR.strings.download_recovery_restart
                null -> MR.strings.download_recovery_check.takeIf { displayStatus == DownloadDisplayStatus.FAILED }
            }
        }
        if (recoveryMessage != null) {
            Text(
                text = stringResource(recoveryMessage),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Preview
@Composable
private fun DownloadQueueScreenPreview() {
    EmptyScreen(
        stringRes = MR.strings.information_no_downloads,
    )
}
