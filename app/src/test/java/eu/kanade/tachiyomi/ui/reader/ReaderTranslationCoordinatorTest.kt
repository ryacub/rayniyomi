package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationStorageManager
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager

/**
 * Contract tests for [ReaderTranslationCoordinator.toggleTranslatedPages]. They pin the
 * cancellation of a racing adjacent-chapter preload, the rebuild ordering, and the
 * preference revert on a failed rebuild.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderTranslationCoordinatorTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    /** Stateful stand-in for the persisted preference, so `toggle()` behaves like production. */
    private class FakeBooleanPreference(initial: Boolean) : Preference<Boolean> {
        private val state = MutableStateFlow(initial)

        override fun key(): String = "pref_show_translated_pages"
        override fun get(): Boolean = state.value
        override fun set(value: Boolean) {
            state.value = value
        }
        override fun isSet(): Boolean = true
        override fun delete() {
            state.value = false
        }
        override fun defaultValue(): Boolean = false
        override fun changes(): Flow<Boolean> = state
        override fun stateIn(scope: CoroutineScope): StateFlow<Boolean> =
            state.stateIn(scope, SharingStarted.Eagerly, state.value)
    }

    /** Loader whose page language follows the live preference, like ChapterLoader in production. */
    private inner class RecordingPageLoader(private val label: String) : PageLoader() {
        val calls = mutableListOf<String>()

        override var isLocal: Boolean = true

        override suspend fun getPages(): List<ReaderPage> {
            calls.add("getPages:$label")
            val suffix = if (preference.get()) "translated" else "original"
            return listOf(ReaderPage(index = 0, url = "$label-$suffix", imageUrl = null))
        }
    }

    private val preference = FakeBooleanPreference(initial = false)

    private val readerPreferences: ReaderPreferences = mockk {
        every { showTranslatedPages() } returns preference
    }

    private class Recorder {
        val shownValues = mutableListOf<Boolean>()
        val events = mutableListOf<String>()
        var reloads = 0
    }

    private fun readerChapter(id: Long, name: String): ReaderChapter =
        ReaderChapter(
            ChapterImpl().apply {
                this.id = id
                manga_id = 1L
                this.name = name
            },
        )

    /**
     * Builds a coordinator over the supplied chapters. Every callback records into the returned
     * [Recorder]; `cancelAdjacentPreload` records its own event so tests can pin ordering.
     */
    private fun buildCoordinator(
        scope: CoroutineScope,
        getViewerChapters: () -> ViewerChapters?,
        cancelAdjacentPreload: suspend () -> Unit = {},
    ): Pair<ReaderTranslationCoordinator, Recorder> {
        val recorder = Recorder()
        val coordinator = ReaderTranslationCoordinator(
            translationStorageManager = mockk<TranslationStorageManager>(relaxed = true),
            translationPreferences = mockk<TranslationPreferences>(relaxed = true),
            translationManager = mockk<TranslationManager>(relaxed = true),
            sourceManager = mockk<MangaSourceManager>(relaxed = true),
            readerPreferences = readerPreferences,
            scope = scope,
            currentManga = { mockk<Manga>(relaxed = true) },
            getCurrChapter = { getViewerChapters()?.currChapter },
            getViewerChapters = getViewerChapters,
            getShowTranslatedPages = { preference.get() },
            chapterIdFlow = MutableStateFlow<Long?>(null),
            onHasTranslationChange = { },
            onTranslationStateChange = { },
            onShowTranslatedPagesChange = { value ->
                recorder.shownValues.add(value)
            },
            onReload = { recorder.reloads++ },
            cancelAdjacentPreload = {
                recorder.events.add("cancel")
                cancelAdjacentPreload()
            },
            ioDispatcher = vt.io,
        )
        return coordinator to recorder
    }

    @Test
    fun `toggle cancels in-flight adjacent preload and rebuilds with new pages`() = runTest(vt.scheduler) {
        val curr = readerChapter(id = 1L, name = "Curr")
        val next = readerChapter(id = 2L, name = "Next")
        curr.state = ReaderChapter.State.Loaded(emptyList())
        next.state = ReaderChapter.State.Loaded(emptyList())
        val currLoader = RecordingPageLoader("curr").also { curr.pageLoader = it }
        val nextLoader = RecordingPageLoader("next").also { next.pageLoader = it }
        val chapters = ViewerChapters(currChapter = curr, prevChapter = null, nextChapter = next)

        // Model the racing preload exactly as production does: a job on the same virtual-time
        // scheduler that writes ReaderChapter.state when it resumes.
        val preloadGate = CompletableDeferred<Unit>()
        val preloadJob = launch(vt.io) {
            preloadGate.await()
            next.state = ReaderChapter.State.Loaded(
                listOf(
                    ReaderPage(index = 0, url = "next-original", imageUrl = null).apply {
                        chapter = next
                    },
                ),
            )
        }

        val (coordinator, recorder) = buildCoordinator(
            scope = this,
            getViewerChapters = { chapters },
            cancelAdjacentPreload = { preloadJob.cancelAndJoin() },
        )

        coordinator.toggleTranslatedPages()
        advanceUntilIdle()
        // Hand the preload its late-write chance; the fix must have closed this window already.
        preloadGate.complete(Unit)
        advanceUntilIdle()

        next.pages!!.single().url shouldBe "next-translated"
        curr.pages!!.single().url shouldBe "curr-translated"
        preloadJob.isCancelled shouldBe true
        recorder.reloads shouldBe 1
    }

    @Test
    fun `toggle cancels the preload before rebuilding any pages`() = runTest(vt.scheduler) {
        val curr = readerChapter(id = 1L, name = "Curr")
        val next = readerChapter(id = 2L, name = "Next")
        curr.state = ReaderChapter.State.Loaded(emptyList())
        next.state = ReaderChapter.State.Loaded(emptyList())
        val currLoader = RecordingPageLoader("curr").also { curr.pageLoader = it }
        val nextLoader = RecordingPageLoader("next").also { next.pageLoader = it }
        val chapters = ViewerChapters(currChapter = curr, prevChapter = null, nextChapter = next)

        val (coordinator, recorder) = buildCoordinator(
            scope = this,
            getViewerChapters = { chapters },
        )

        coordinator.toggleTranslatedPages()
        advanceUntilIdle()

        recorder.events.first() shouldBe "cancel"
        (currLoader.calls + nextLoader.calls).first() shouldBe "getPages:curr"
        recorder.events.count { it == "cancel" } shouldBe 1
    }

    @Test
    fun `toggle rebuilds a loaded prevChapter the same way it rebuilds nextChapter`() = runTest(vt.scheduler) {
        val prev = readerChapter(id = 1L, name = "Prev")
        val curr = readerChapter(id = 2L, name = "Curr")
        prev.state = ReaderChapter.State.Loaded(emptyList())
        curr.state = ReaderChapter.State.Loaded(emptyList())
        val prevLoader = RecordingPageLoader("prev").also { prev.pageLoader = it }
        val currLoader = RecordingPageLoader("curr").also { curr.pageLoader = it }
        val chapters = ViewerChapters(currChapter = curr, prevChapter = prev, nextChapter = null)

        val (coordinator, recorder) = buildCoordinator(
            scope = this,
            getViewerChapters = { chapters },
        )

        coordinator.toggleTranslatedPages()
        advanceUntilIdle()

        prev.pages!!.single().url shouldBe "prev-translated"
        curr.pages!!.single().url shouldBe "curr-translated"
        prevLoader.calls shouldBe listOf("getPages:prev")
        currLoader.calls shouldBe listOf("getPages:curr")
        recorder.reloads shouldBe 1
    }

    @Test
    fun `a failed current-chapter rebuild reverts the preference`() = runTest(vt.scheduler) {
        val curr = readerChapter(id = 1L, name = "Curr")
        curr.state = ReaderChapter.State.Loaded(emptyList())
        val currLoader = object : PageLoader() {
            override var isLocal: Boolean = true
            override suspend fun getPages(): List<ReaderPage> = emptyList()
        }.also { curr.pageLoader = it }
        val chapters = ViewerChapters(currChapter = curr, prevChapter = null, nextChapter = null)

        val (coordinator, recorder) = buildCoordinator(
            scope = this,
            getViewerChapters = { chapters },
        )

        coordinator.toggleTranslatedPages()
        advanceUntilIdle()

        preference.get() shouldBe false
        recorder.shownValues shouldBe listOf(true, false)
        recorder.reloads shouldBe 0
    }

    @Test
    fun `a second toggle cancels the first in-flight toggle job`() = runTest(vt.scheduler) {
        val curr = readerChapter(id = 1L, name = "Curr")
        val next = readerChapter(id = 2L, name = "Next")
        curr.state = ReaderChapter.State.Loaded(emptyList())
        next.state = ReaderChapter.State.Loaded(emptyList())
        val hangGate = CompletableDeferred<Unit>()
        val currLoader = object : PageLoader() {
            val calls = mutableListOf<String>()

            override var isLocal: Boolean = true

            override suspend fun getPages(): List<ReaderPage> {
                calls.add("getPages:curr")
                hangGate.await()
                val suffix = if (preference.get()) "translated" else "original"
                return listOf(ReaderPage(index = 0, url = "curr-$suffix", imageUrl = null))
            }
        }.also { curr.pageLoader = it }
        val nextLoader = RecordingPageLoader("next").also { next.pageLoader = it }
        val chapters = ViewerChapters(currChapter = curr, prevChapter = null, nextChapter = next)

        val (coordinator, recorder) = buildCoordinator(
            scope = this,
            getViewerChapters = { chapters },
        )

        coordinator.toggleTranslatedPages()
        advanceUntilIdle()
        coordinator.toggleTranslatedPages()
        advanceUntilIdle()
        hangGate.complete(Unit)
        advanceUntilIdle()

        preference.get() shouldBe false
        curr.pages!!.single().url shouldBe "curr-original"
        next.pages!!.single().url shouldBe "next-original"
        currLoader.calls.size shouldBe 2
        nextLoader.calls.size shouldBe 1
        recorder.reloads shouldBe 1
        recorder.shownValues shouldBe listOf(true, false)
    }
}
