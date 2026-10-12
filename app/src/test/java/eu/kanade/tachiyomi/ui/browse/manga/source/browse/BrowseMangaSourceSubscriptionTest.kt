package eu.kanade.tachiyomi.ui.browse.manga.source.browse

import androidx.lifecycle.ViewModelStore
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.test.create
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.service.LibraryPreferences
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseMangaSourceSubscriptionTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() = vt.setUpMain()

    @AfterEach
    fun tearDown() = vt.tearDownMain()

    @Test
    fun `rows release database subscriptions when no row is visible`() = runTest(vt.scheduler) {
        val persisted = Manga.create().copy(id = 7L, source = 1L, url = "/7", title = "Stored title")
        val database = MutableStateFlow(persisted)
        val active = AtomicInteger()
        val started = Channel<Unit>(Channel.UNLIMITED)
        val stopped = Channel<Unit>(Channel.UNLIMITED)
        val changes = flow<Manga?> {
            active.incrementAndGet()
            started.send(Unit)
            try {
                database.collect { emit(it) }
            } finally {
                active.decrementAndGet()
                stopped.trySend(Unit)
            }
        }
        val store = ViewModelStore()
        val model = store.create { createModel(persisted, changes) }
        val differ = AsyncPagingDataDiffer(
            diffCallback = object : DiffUtil.ItemCallback<StateFlow<Manga>>() {
                override fun areItemsTheSame(oldItem: StateFlow<Manga>, newItem: StateFlow<Manga>) =
                    oldItem.value.id == newItem.value.id
                override fun areContentsTheSame(oldItem: StateFlow<Manga>, newItem: StateFlow<Manga>) =
                    oldItem.value == newItem.value
            },
            updateCallback = object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) = Unit
                override fun onRemoved(position: Int, count: Int) = Unit
                override fun onMoved(fromPosition: Int, toPosition: Int) = Unit
                override fun onChanged(position: Int, count: Int, payload: Any?) = Unit
            },
            mainDispatcher = Dispatchers.Unconfined,
            workerDispatcher = Dispatchers.Unconfined,
        )
        val presented = CompletableDeferred<Unit>()
        val presentationJob = backgroundScope.launch(Dispatchers.Unconfined) {
            differ.onPagesUpdatedFlow.collect {
                if (differ.itemCount > 0) presented.complete(Unit)
            }
        }
        val pagingJob = backgroundScope.launch(Dispatchers.Unconfined) {
            model.mangaPagerFlowFlow.flatMapLatest { it }.collectLatest { differ.submitData(it) }
        }
        var rowJob: Job? = null
        try {
            withContext(Dispatchers.Default) { withTimeout(5_000) { presented.await() } }
            receiveSignal(started)
            val row = differ.snapshot().items.single()
            assertEquals("Stored title", row.value.title)
            assertEquals(false, row.value.favorite)
            assertEquals(0, active.get(), "An offscreen row must not hold a database subscription")
            receiveSignal(stopped)

            rowJob = backgroundScope.launch(Dispatchers.Unconfined) { row.collect {} }
            receiveSignal(started)
            assertEquals(1, active.get())
            database.value = persisted.copy(favorite = true, title = "Library title")
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { row.first { it.favorite && it.title == "Library title" } }
            }

            rowJob.cancelAndJoin()
            receiveSignal(stopped)
            assertEquals(0, active.get())
        } finally {
            rowJob?.cancelAndJoin()
            pagingJob.cancelAndJoin()
            presentationJob.cancelAndJoin()
            store.clear()
        }
    }

    private suspend fun receiveSignal(channel: Channel<Unit>) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { channel.receive() }
    }

    private fun createModel(persisted: Manga, changes: Flow<Manga?>): BrowseMangaSourceScreenModel {
        val remote = SManga.create().apply {
            url = persisted.url
            title = "Source title"
        }
        return BrowseMangaSourceScreenModel(
            sourceId = persisted.source,
            listingQuery = "query",
            sourceManager = mockk(relaxed = true) { every { getOrStub(any()) } returns mockk(relaxed = true) },
            sourcePreferences = SourcePreferences(InMemoryPreferenceStore()),
            libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()),
            coverCache = mockk(relaxed = true),
            getRemoteManga = mockk {
                every { subscribe(any(), any(), any()) } answers {
                    object : PagingSource<Long, SManga>() {
                        override suspend fun load(params: LoadParams<Long>): LoadResult<Long, SManga> =
                            LoadResult.Page(listOf(remote), null, null)
                        override fun getRefreshKey(state: PagingState<Long, SManga>): Long? = null
                    }
                }
            },
            getDuplicateLibraryManga = mockk(relaxed = true),
            getCategories = mockk(relaxed = true),
            setMangaCategories = mockk(relaxed = true),
            setMangaDefaultChapterFlags = mockk(relaxed = true),
            getManga = GetManga(
                mockk { every { getMangaByUrlAndSourceIdAsFlow(persisted.url, persisted.source) } returns changes },
            ),
            networkToLocalManga = mockk {
                coEvery { await(any()) } returns persisted.copy(title = "Source title")
            },
            updateManga = mockk(relaxed = true),
            addTracks = mockk(relaxed = true),
            getIncognitoState = mockk { every { await(any()) } returns true },
            getFavorites = mockk { every { subscribe(any()) } returns emptyFlow() },
        )
    }
}
