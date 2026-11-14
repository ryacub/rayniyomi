package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.data.translation.TranslationState
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

/**
 * Contract tests for [ReaderTranslationCoordinator.toggleTranslatedPages]. They pin the
 * restart of a racing adjacent-chapter load, the rebuild ordering, and the preference revert
 * on a failed rebuild. Reloads go through a real [ChapterLoader].
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

    /**
     * Records every reducer output into [updates]. The per-field views keep one entry per update
     * whose field differs from the previous value, matching what the old one-line setter
     * callbacks recorded.
     */
    private class Recorder {
        var slice = ReaderTranslationUiState()
        val updates = mutableListOf<ReaderTranslationUiState>()
        var reloads = 0

        val shownValues: List<Boolean>
            get() = updates.map { it.showTranslatedPages }.changesFrom(ReaderTranslationUiState().showTranslatedPages)
        val hasTranslationValues: List<Boolean>
            get() = updates.map { it.hasTranslation }.changesFrom(ReaderTranslationUiState().hasTranslation)

        private fun <T> List<T>.changesFrom(first: T): List<T> {
            var previous = first
            return mapNotNull { value ->
                if (value == previous) return@mapNotNull null
                previous = value
                value
            }
        }
    }

    private fun readerChapter(id: Long, name: String): ReaderChapter =
        ReaderChapter(
            Chapter.create().copy(id = id, mangaId = 1L, name = name),
        )

    /**
     * A loader that only reuses the chapters' own page loaders, as a translation reload does.
     * The loader's supervisor job stays active until its scope ends, so under runTest it gets
     * its own scope on the test scheduler instead of a child of the test.
     */
    private fun chapterLoader(scope: CoroutineScope) = ChapterLoader(
        context = mockk(relaxed = true),
        downloadManager = mockk(relaxed = true),
        downloadProvider = mockk(relaxed = true),
        manga = Manga.create(),
        source = mockk(relaxed = true),
        scope = if (scope is TestScope) CoroutineScope(SupervisorJob() + vt.io) else scope,
        ioDispatcher = vt.io,
    )

    /**
     * Builds a coordinator over the supplied chapters. Every callback records into the returned
     * [Recorder].
     */
    private fun buildCoordinator(
        scope: CoroutineScope,
        getViewerChapters: () -> ViewerChapters?,
        chapterLoader: ChapterLoader = chapterLoader(scope),
        translationManager: TranslationManager = mockk(relaxed = true),
        isChapterTranslated: Boolean = false,
    ): Pair<ReaderTranslationCoordinator, Recorder> {
        val recorder = Recorder()
        val coordinator = ReaderTranslationCoordinator(
            translationManager = translationManager,
            readerPreferences = readerPreferences,
            scope = scope,
            getViewerChapters = getViewerChapters,
            hasTranslationFor = { isChapterTranslated },
            chapterIdFlow = MutableStateFlow<Long?>(null),
            updateTranslation = { reduce ->
                val next = reduce(recorder.slice)
                recorder.updates.add(next)
                recorder.slice = next
            },
            onReload = { recorder.reloads++ },
            reloadChapter = chapterLoader::reloadChapter,
            ioDispatcher = vt.io,
        )
        return coordinator to recorder
    }

    @Test
    fun `toggle restarts an in-flight adjacent load and rebuilds with new pages`() = runTest(vt.scheduler) {
        val curr = readerChapter(id = 1L, name = "Curr")
        val next = readerChapter(id = 2L, name = "Next")
        curr.state = ReaderChapter.State.Loaded(emptyList())
        next.state = ReaderChapter.State.Loaded(emptyList())
        RecordingPageLoader("curr").also { curr.pageLoader = it }
        val chapters = ViewerChapters(currChapter = curr, prevChapter = null, nextChapter = next)

        // The racing load is an owner attempt whose page list resumes only after the toggle.
        val preloadGate = CompletableDeferred<Unit>()
        var nextLoads = 0
        next.pageLoader = object : PageLoader() {
            override var isLocal: Boolean = true

            override suspend fun getPages(): List<ReaderPage> {
                if (++nextLoads == 1) preloadGate.await()
                val suffix = if (preference.get()) "translated" else "original"
                return listOf(ReaderPage(index = 0, url = "next-$suffix", imageUrl = null))
            }
        }
        val chapterLoader = chapterLoader(this)
        val racingLoad = launch { chapterLoader.reloadChapter(next) }
        advanceUntilIdle()

        val (coordinator, recorder) = buildCoordinator(
            scope = this,
            getViewerChapters = { chapters },
            chapterLoader = chapterLoader,
        )

        coordinator.toggleTranslatedPages()
        advanceUntilIdle()
        // Hand the preload its late-write chance; the owner must have closed this window already.
        preloadGate.complete(Unit)
        advanceUntilIdle()

        next.pages!!.single().url shouldBe "next-translated"
        curr.pages!!.single().url shouldBe "curr-translated"
        racingLoad.isCancelled shouldBe true
        recorder.reloads shouldBe 1
    }

    @Test
    fun `toggle reloads the current chapter before any neighbour`() = runTest(vt.scheduler) {
        val curr = readerChapter(id = 1L, name = "Curr")
        val next = readerChapter(id = 2L, name = "Next")
        curr.state = ReaderChapter.State.Loaded(emptyList())
        next.state = ReaderChapter.State.Loaded(emptyList())
        val currLoader = RecordingPageLoader("curr").also { curr.pageLoader = it }
        val nextLoader = RecordingPageLoader("next").also { next.pageLoader = it }
        val chapters = ViewerChapters(currChapter = curr, prevChapter = null, nextChapter = next)

        val (coordinator, _) = buildCoordinator(
            scope = this,
            getViewerChapters = { chapters },
        )

        coordinator.toggleTranslatedPages()
        advanceUntilIdle()

        (currLoader.calls + nextLoader.calls).first() shouldBe "getPages:curr"
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

    /**
     * Starts the coordinator against a Loaded current chapter, bumps languageGeneration once,
     * and advances the scheduler. Returns the recorder and the current chapter. Call inside
     * runTest; the collector scope is cancelled before the assertions run.
     */
    private suspend fun TestScope.bumpLanguageGeneration(shown: Boolean): Pair<Recorder, ReaderChapter> {
        val curr = readerChapter(id = 1L, name = "Curr")
        curr.state = ReaderChapter.State.Loaded(emptyList())
        RecordingPageLoader("curr").also { curr.pageLoader = it }
        val chapters = ViewerChapters(currChapter = curr, prevChapter = null, nextChapter = null)
        preference.set(shown)
        val generations = MutableStateFlow(0)
        val translationManager = mockk<TranslationManager> {
            every { languageGeneration } returns generations
            every { translationStates } returns MutableStateFlow<Map<Long, TranslationState>>(emptyMap())
        }
        // A dedicated scope holds start()'s infinite collectors; cancelling it keeps runTest
        // from seeing unfinished children of the test scope.
        val collectorScope = CoroutineScope(vt.io + SupervisorJob())

        try {
            val (coordinator, recorder) = buildCoordinator(
                scope = collectorScope,
                getViewerChapters = { chapters },
                translationManager = translationManager,
                isChapterTranslated = true,
            )

            coordinator.start()
            advanceUntilIdle()
            generations.value += 1
            advanceUntilIdle()
            return recorder to curr
        } finally {
            collectorScope.cancel()
        }
    }

    @Test
    fun `a language generation change refreshes hasTranslation and reloads when translations are shown`() =
        runTest(vt.scheduler) {
            val (recorder, curr) = bumpLanguageGeneration(shown = true)

            recorder.hasTranslationValues shouldBe listOf(true)
            curr.pages!!.single().url shouldBe "curr-translated"
            recorder.reloads shouldBe 1
        }

    @Test
    fun `a language generation change updates hasTranslation without reload when translations are hidden`() =
        runTest(vt.scheduler) {
            val (recorder, curr) = bumpLanguageGeneration(shown = false)

            recorder.hasTranslationValues shouldBe listOf(true)
            curr.state.shouldBeInstanceOf<ReaderChapter.State.Loaded>()
            recorder.reloads shouldBe 0
        }
}
