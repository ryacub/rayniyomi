package eu.kanade.tachiyomi.ui.storage

import eu.kanade.presentation.more.storage.StorageScreenState
import eu.kanade.tachiyomi.test.VirtualTime
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences
import java.util.concurrent.CopyOnWriteArrayList

class CommonStorageScreenModelTest {

    private val vt = VirtualTime()

    private data class Entry(val id: Long, val size: Long)

    private class TestStorageScreenModel(
        libraries: List<Entry>,
        librariesGate: CompletableDeferred<Unit>,
        getDownloadSize: Entry.() -> Long,
    ) : CommonStorageScreenModel<Entry>(
        downloadCacheChanges = MutableStateFlow(Unit),
        downloadCacheIsInitializing = MutableStateFlow(false),
        libraries = flow {
            librariesGate.await()
            emit(libraries)
        },
        categories = { flowOf(listOf(category)) },
        getDownloadSize = getDownloadSize,
        getDownloadCount = { 1 },
        getId = { id },
        getCategoryId = { category.id },
        getTitle = { "entry $id" },
        getThumbnail = { null },
        libraryPreferences = mockk<LibraryPreferences> {
            every { hideHiddenCategoriesSettings().get() } returns false
        },
    ) {
        override fun deleteEntry(id: Long) = Unit
    }

    @BeforeEach
    fun setUp() = vt.setUpMain()

    @AfterEach
    fun tearDown() = vt.tearDownMain()

    @Test
    fun `state never shows a partial list while sizes are computed`() = runBlocking {
        val entries = listOf(Entry(1, 10), Entry(2, 30), Entry(3, 20))
        val gate = CompletableDeferred<Unit>()
        val itemCountsSeenBySizeCalls = CopyOnWriteArrayList<Int>()
        lateinit var model: TestStorageScreenModel
        model = TestStorageScreenModel(entries, gate) {
            itemCountsSeenBySizeCalls += (model.state.value as? StorageScreenState.Success)?.items?.size ?: 0
            size
        }

        gate.complete(Unit)
        val state = awaitItems(model, count = 3)

        assertEquals(listOf(0, 0, 0), itemCountsSeenBySizeCalls.toList())
        assertEquals(listOf(30L, 20L, 10L), state.items.map { it.size })
    }

    private suspend fun awaitItems(model: TestStorageScreenModel, count: Int): StorageScreenState.Success =
        withTimeout(5_000) {
            model.state.first { it is StorageScreenState.Success && it.items.size == count }
                as StorageScreenState.Success
        }

    private companion object {
        val category = Category(id = 1L, name = "Default", order = 0L, flags = 0L, hidden = false)
    }
}
