package eu.kanade.tachiyomi.ui.download

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.data.download.model.DownloadRecovery
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

@RunWith(AndroidJUnit4::class)
class DownloadQueueScreenAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun draggingCollapsedHeaderMovesItsWholeSourceGroup() {
        val groups = mutableStateOf(
            listOf(
                Group(1, listOf(Entry(11), Entry(12))),
                Group(2, listOf(Entry(21))),
            ),
        )
        var reorderedIds: List<Long>? = null
        val actions = Actions { ids ->
            reorderedIds = ids
            groups.value = groups.value.sortedBy { group -> ids.indexOf(group.downloads.first().downloadId) }
        }
        val nestedScroll = object : NestedScrollConnection {}
        val dragLabel = InstrumentationRegistry.getInstrumentation().targetContext.stringResource(MR.strings.action_sort)
        composeRule.setContent {
            MaterialTheme {
                DownloadQueueScreen(
                    contentPadding = PaddingValues(),
                    screenModel = actions,
                    downloadList = groups.value,
                    nestedScrollConnection = nestedScroll,
                )
            }
        }

        val handles = composeRule.onAllNodesWithContentDescription(dragLabel)
        val from = handles[0].fetchSemanticsNode().boundsInRoot.center
        val destination = handles[1].fetchSemanticsNode().boundsInRoot
        val to = Offset(destination.center.x, destination.bottom - 1f)
        composeRule.onRoot().performTouchInput { swipe(from, to, durationMillis = 400) }

        composeRule.runOnIdle {
            assertEquals("Drag must move the source and keep its download order", listOf(21L, 11L, 12L), reorderedIds)
        }
    }

    private data class Group(
        override val sourceId: Long,
        override val downloads: List<Entry>,
    ) : DownloadQueueGroup<Entry> {
        override val sourceName = "Source $sourceId"
        override val isExpanded = false
    }

    private data class Entry(override val downloadId: Long) : DownloadQueueEntry {
        override val entryId = downloadId
        override val key = downloadId.toString()
        override val name = "Download $downloadId"
        override val progress = 0f
        override val recovery = MutableStateFlow<DownloadRecovery?>(null)
        override val displayStatus = MutableStateFlow(DownloadDisplayStatus.WAITING_FOR_SLOT)
        override val canRetryBlocked = false
    }

    private class Actions(private val onReorder: (List<Long>) -> Unit) : DownloadQueueActions<Entry> {
        override fun reorder(downloadIds: List<Long>) = onReorder(downloadIds)
        override fun collapseAll() = Unit
        override fun expandHeader(sourceId: Long) = Unit
        override fun toggleExpanded(sourceId: Long) = Unit
        override fun moveToTop(item: Entry) = Unit
        override fun moveToBottom(item: Entry) = Unit
        override fun moveToTopSeries(entryId: Long) = Unit
        override fun moveToBottomSeries(entryId: Long) = Unit
        override fun retryDownload(item: Entry) = Unit
        override fun cancelDownload(item: Entry) = Unit
        override fun cancelSeries(entryId: Long) = Unit
    }
}
