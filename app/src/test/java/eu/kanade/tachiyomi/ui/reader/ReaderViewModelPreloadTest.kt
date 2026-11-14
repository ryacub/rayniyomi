package eu.kanade.tachiyomi.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ReaderViewModelPreloadTest {

    private val vt = VirtualTime()

    private val previousGraph = AppGraphHolder.graphOrNull

    @BeforeEach
    fun setUp() = vt.setUpMain()

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
        AppGraphHolder.graphOrNull = previousGraph
    }

    private val showTranslatedPages = FakeBooleanPreference(initial = false)

    private fun readerViewModel(): ReaderViewModel {
        val readerPreferences = mockk<ReaderPreferences>(relaxed = true) {
            every { defaultOrientationType().get() } returns ReaderOrientation.FREE.flagValue
            every { showTranslatedPages() } returns showTranslatedPages
        }
        val translationManager = mockk<TranslationManager> {
            every { languageGeneration } returns MutableStateFlow(0)
            every { translationStates } returns MutableStateFlow(emptyMap())
        }
        val downloadPreferences = mockk<DownloadPreferences> {
            every { autoDownloadWhileReading().get() } returns 0
        }
        return ReaderViewModel(
            savedState = SavedStateHandle(),
            sourceManager = mockk(relaxed = true),
            downloadManager = mockk(relaxed = true),
            downloadProvider = mockk(relaxed = true),
            imageSaver = mockk(relaxed = true),
            readerPreferences = readerPreferences,
            basePreferences = mockk(relaxed = true),
            downloadPreferences = downloadPreferences,
            trackPreferences = mockk(relaxed = true),
            trackChapter = mockk(relaxed = true),
            getManga = mockk(relaxed = true),
            getChaptersByMangaId = mockk(relaxed = true),
            getNextChapters = mockk(relaxed = true),
            upsertHistory = mockk(relaxed = true),
            updateChapter = mockk(relaxed = true),
            setMangaViewerFlags = mockk(relaxed = true),
            getIncognitoState = mockk(relaxed = true),
            libraryPreferences = mockk(relaxed = true),
            translationStorageManager = mockk(relaxed = true),
            translationPreferences = mockk(relaxed = true),
            translationManager = translationManager,
            application = mockk(relaxed = true),
        )
    }

    private fun ReaderViewModel.useChapterLoader(chapterLoader: ChapterLoader) {
        ReaderViewModel::class.java.getDeclaredField("loader").apply { isAccessible = true }
            .set(this, chapterLoader)
    }

    @Test
    fun `a repeated preload of a loading chapter keeps the load running`() = runTest(vt.scheduler) {
        val chapter = ReaderChapter(Chapter.create().copy(id = 3L, mangaId = 1L))
        val loadStarted = CompletableDeferred<Unit>()
        val loadCancelled = CompletableDeferred<Unit>()
        val chapterLoader = mockk<ChapterLoader> {
            coEvery { loadChapter(chapter) } coAnswers {
                chapter.state = ReaderChapter.State.Loading
                loadStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    loadCancelled.complete(Unit)
                }
            }
        }
        val model = readerViewModel().apply { useChapterLoader(chapterLoader) }
        try {
            model.preload(chapter)
            withContext(Dispatchers.Default) { withTimeout(5_000) { loadStarted.await() } }

            model.preload(chapter)

            val cancelled = withContext(Dispatchers.Default) { withTimeoutOrNull(500) { loadCancelled.await() } }
            withClue("the second preload cancelled the first load") { cancelled shouldBe null }
        } finally {
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun `a preload of the other adjacent chapter starts while a load runs`() = runTest(vt.scheduler) {
        val loadingChapter = ReaderChapter(Chapter.create().copy(id = 3L, mangaId = 1L))
        val otherChapter = ReaderChapter(Chapter.create().copy(id = 4L, mangaId = 1L))
        val firstLoadStarted = CompletableDeferred<Unit>()
        val firstLoadCancelled = CompletableDeferred<Unit>()
        val otherLoadStarted = CompletableDeferred<Unit>()
        val chapterLoader = mockk<ChapterLoader> {
            coEvery { loadChapter(loadingChapter) } coAnswers {
                loadingChapter.state = ReaderChapter.State.Loading
                firstLoadStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    firstLoadCancelled.complete(Unit)
                }
            }
            coEvery { loadChapter(otherChapter) } coAnswers { otherLoadStarted.complete(Unit) }
        }
        val model = readerViewModel().apply { useChapterLoader(chapterLoader) }
        try {
            model.preload(loadingChapter)
            withContext(Dispatchers.Default) { withTimeout(5_000) { firstLoadStarted.await() } }

            model.preload(otherChapter)

            withContext(Dispatchers.Default) { withTimeout(5_000) { otherLoadStarted.await() } }

            val cancelled = withContext(Dispatchers.Default) {
                withTimeoutOrNull(500) { firstLoadCancelled.await() }
            }
            withClue("the preload of the other chapter cancelled the first load") { cancelled shouldBe null }
        } finally {
            model.viewModelScope.cancel()
        }
    }

    @Test
    fun `a translation toggle restarts the in-flight preload with the new preference`() = runTest(vt.scheduler) {
        val curr = ReaderChapter(Chapter.create().copy(id = 1L, mangaId = 1L, name = "Curr"))
        val next = ReaderChapter(Chapter.create().copy(id = 2L, mangaId = 1L, name = "Next"))
        curr.pageLoader = PreferencePageLoader("curr")
        curr.state = ReaderChapter.State.Loaded(curr.pageLoader!!.getPages())
        val source = GatedTranslatedSource("next")
        val model = readerViewModel().apply {
            useChapterLoader(realChapterLoader(source.source, viewModelScope))
            showChapters(ViewerChapters(currChapter = curr, prevChapter = null, nextChapter = next))
        }
        try {
            model.preload(next)
            withContext(Dispatchers.Default) { withTimeout(5_000) { source.firstRequest.await() } }

            model.toggleTranslatedPages()

            val restarted = withContext(Dispatchers.Default) {
                withTimeoutOrNull(5_000) { source.secondRequest.await() }
            }
            withClue("the toggle did not restart the in-flight load; next is ${next.state}") {
                restarted.shouldNotBeNull()
            }
            next.state shouldBe ReaderChapter.State.Loading

            source.openSecondRequest()
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { next.stateFlow.first { it is ReaderChapter.State.Loaded } }
            }
            next.pages!!.single().url shouldBe "next-translated"
            curr.pages!!.single().url shouldBe "curr-translated"
            source.requests shouldBe 2
        } finally {
            model.viewModelScope.cancel()
        }
    }

    private fun ReaderViewModel.showChapters(chapters: ViewerChapters) {
        @Suppress("UNCHECKED_CAST")
        val state = ReaderViewModel::class.java.getDeclaredField("mutableState").apply { isAccessible = true }
            .get(this) as MutableStateFlow<ReaderViewModel.State>
        state.value = ReaderViewModel.State(viewerChapters = chapters)
    }

    private fun realChapterLoader(source: HttpSource, scope: CoroutineScope): ChapterLoader {
        AppGraphHolder.graph = mockk<AppGraph>(relaxed = true) {
            every { chapterCache } returns mockk<ChapterCache> {
                every { getPageListFromCache(any()) } throws IOException("not cached")
            }
            every { sourcePreferences } returns SourcePreferences(InMemoryPreferenceStore())
        }
        return ChapterLoader(
            context = mockk(relaxed = true),
            downloadManager = mockk<MangaDownloadManager> {
                every { isChapterDownloaded(any(), any(), any(), any(), any()) } returns false
            },
            downloadProvider = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, source = 1L),
            source = source,
            scope = scope,
        )
    }

    /** A source whose first page-list request waits until cancelled, and whose pages follow the preference. */
    private inner class GatedTranslatedSource(private val label: String) {
        private val counter = AtomicInteger()
        private val secondGate = CompletableDeferred<Unit>()
        val firstRequest = CompletableDeferred<Unit>()
        val secondRequest = CompletableDeferred<Unit>()
        val requests: Int get() = counter.get()

        fun openSecondRequest() = secondGate.complete(Unit)

        val source: HttpSource = mockk(relaxed = true) {
            coEvery { getPageList(any()) } coAnswers {
                if (counter.incrementAndGet() == 1) {
                    firstRequest.complete(Unit)
                    awaitCancellation()
                }
                secondRequest.complete(Unit)
                secondGate.await()
                listOf(Page(index = 0, url = "$label-${languageSuffix()}", imageUrl = "image"))
            }
        }
    }

    /** Page loader whose page language follows the live preference, like DownloadPageLoader. */
    private inner class PreferencePageLoader(private val label: String) : PageLoader() {
        override var isLocal: Boolean = true

        override suspend fun getPages(): List<ReaderPage> =
            listOf(ReaderPage(index = 0, url = "$label-${languageSuffix()}", imageUrl = null))
    }

    private fun languageSuffix() = if (showTranslatedPages.get()) "translated" else "original"

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
}
