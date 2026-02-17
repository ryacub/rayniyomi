package eu.kanade.tachiyomi.ui.entries.common

import eu.kanade.tachiyomi.data.track.AnimeTracker
import eu.kanade.tachiyomi.data.track.Tracker
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EntryCommonHelpersTest {

    @Test
    fun `selection controller applies range selection on long press`() {
        val selectedIds = mutableSetOf<Long>()
        val controller = EntrySelectionController(selectedIds)
        val items = (1L..5L).map { TestSelectableItem(id = it, selected = false) }

        val firstSelected = controller.toggleSelection(
            items = items,
            itemId = 1L,
            selected = true,
            userSelected = true,
            fromLongPress = true,
            updateSelection = { item, selected -> item.copy(selected = selected) },
        )

        val rangeSelected = controller.toggleSelection(
            items = firstSelected,
            itemId = 4L,
            selected = true,
            userSelected = true,
            fromLongPress = true,
            updateSelection = { item, selected -> item.copy(selected = selected) },
        )

        assertTrue(rangeSelected.first { it.id == 1L }.selected)
        assertTrue(rangeSelected.first { it.id == 2L }.selected)
        assertTrue(rangeSelected.first { it.id == 3L }.selected)
        assertTrue(rangeSelected.first { it.id == 4L }.selected)
        assertFalse(rangeSelected.first { it.id == 5L }.selected)
        assertEquals(setOf(1L, 2L, 3L, 4L), selectedIds)
    }

    @Test
    fun `gap separator inserts missing count for ascending list`() {
        val items = listOf(
            NumberedItem(1L, 1.0),
            NumberedItem(2L, 4.0),
        )

        val results = EntryListGapSeparator.withMissingCount(
            items = items,
            sortDescending = false,
            itemId = { it.id },
            itemNumber = { it.number },
            calculateGap = { higher, lower -> (higher.number - lower.number).toInt() - 1 },
            mapItem = { SeparatorResult.Item(it.id) },
            mapMissing = { id, count -> SeparatorResult.Missing(id, count) },
        )

        assertEquals(3, results.size)
        assertEquals(SeparatorResult.Item(1L), results[0])
        assertEquals(SeparatorResult.Missing("1-2", 2), results[1])
        assertEquals(SeparatorResult.Item(2L), results[2])
    }

    @Test
    fun `gap separator does not insert leading gap for descending list`() {
        val items = listOf(
            NumberedItem(2L, 4.0),
            NumberedItem(1L, 1.0),
        )

        val results = EntryListGapSeparator.withMissingCount(
            items = items,
            sortDescending = true,
            itemId = { it.id },
            itemNumber = { it.number },
            calculateGap = { higher, lower -> (higher.number - lower.number).toInt() - 1 },
            mapItem = { SeparatorResult.Item(it.id) },
            mapMissing = { id, count -> SeparatorResult.Missing(id, count) },
        )

        assertEquals(3, results.size)
        assertEquals(SeparatorResult.Item(2L), results[0])
        assertEquals(SeparatorResult.Missing("1-2", 2), results[1])
        assertEquals(SeparatorResult.Item(1L), results[2])
    }

    @Test
    fun `download state updater replaces only matching item`() {
        val items = listOf(
            DownloadItem(id = 1L, value = 10),
            DownloadItem(id = 2L, value = 20),
        )

        val updated = EntryDownloadStateUpdater.update(
            items = items,
            entryId = 2L,
            idSelector = { it.id },
            updateItem = { it.copy(value = 99) },
        )

        assertEquals(10, updated[0].value)
        assertEquals(99, updated[1].value)
    }

    @Test
    fun `tracking summary counts only supported trackers`() {
        val trackRows = listOf(TrackRow(1L), TrackRow(2L), TrackRow(3L))
        val loggedIn = listOf<Tracker>(
            TestTracker(id = 1L),
            TestTracker(id = 4L),
        )

        val summary = EntryTrackingSummaryObserver.summarize(
            tracks = trackRows,
            loggedInTrackers = loggedIn,
            trackId = { it.trackerId },
            isTrackerSupported = { it.id != 4L },
        )

        assertEquals(1, summary.trackingCount)
        assertTrue(summary.hasLoggedInTrackers)
    }

    private data class TestSelectableItem(
        override val id: Long,
        override val selected: Boolean,
    ) : SelectableEntryItem

    private data class NumberedItem(
        val id: Long,
        val number: Double,
    )

    private sealed interface SeparatorResult {
        data class Item(val id: Long) : SeparatorResult
        data class Missing(val id: String, val count: Int) : SeparatorResult
    }

    private data class DownloadItem(
        val id: Long,
        val value: Int,
    )

    private data class TrackRow(
        val trackerId: Long,
    )

    private class TestTracker(
        override val id: Long,
    ) : Tracker {
        override val name: String = "test"
        override val client: OkHttpClient = mockk()
        override val supportsReadingDates: Boolean = false
        override val supportsPrivateTracking: Boolean = false
        override val isLoggedIn: Boolean = true
        override val isLoggedInFlow: Flow<Boolean> = flowOf(true)

        override fun getLogo(): Int = 0

        override fun getLogoColor(): Int = 0

        override fun getCompletionStatus(): Long = 0L

        override fun getScoreList(): ImmutableList<String> {
            throw UnsupportedOperationException("not needed")
        }

        override suspend fun login(username: String, password: String) {
            throw UnsupportedOperationException("not needed")
        }

        override fun logout() {
            // no-op for tests
        }

        override fun getUsername(): String = ""

        override fun getPassword(): String = ""

        override fun saveCredentials(username: String, password: String) {
            // no-op for tests
        }

        override val animeService: AnimeTracker
            get() = throw UnsupportedOperationException("not needed")
    }
}
