package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.translation.TranslationStorageManager
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.ui.reader.loader.ChapterLoader
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager

class ReaderTranslationReloadTest {

    private fun readerChapter(lastPageRead: Int = 0): ReaderChapter {
        val chapter = Chapter.create().copy(
            id = 1L,
            mangaId = 1L,
            name = "Chapter 1",
            lastPageRead = lastPageRead.toLong(),
        )
        return ReaderChapter(chapter)
    }

    private fun readerPage(index: Int): ReaderPage = ReaderPage(index)

    /**
     * Reloads through a real owner. Every chapter here has a page loader, so none is opened.
     * The owner's supervisor job stays active until its scope ends, so it gets its own scope.
     */
    private fun reloader(): suspend (ReaderChapter) -> Unit = ChapterLoader(
        context = mockk(relaxed = true),
        downloadManager = mockk(relaxed = true),
        downloadProvider = mockk(relaxed = true),
        manga = Manga.create(),
        source = mockk(relaxed = true),
        scope = CoroutineScope(SupervisorJob()),
    )::reloadChapter

    private class FakeLoader(val action: () -> List<ReaderPage>) : PageLoader() {
        var calls = 0

        override var isLocal: Boolean = true

        override suspend fun getPages(): List<ReaderPage> {
            calls++
            return action()
        }
    }

    @Test
    fun `a toggle rebuilds the current chapter and both loaded neighbours`() = runTest {
        val prev = readerChapter()
        val curr = readerChapter()
        val next = readerChapter()
        val original = listOf(readerPage(0))
        val prevFresh = listOf(readerPage(1))
        val currFresh = listOf(readerPage(2))
        val nextFresh = listOf(readerPage(3))
        prev.state = ReaderChapter.State.Loaded(original)
        curr.state = ReaderChapter.State.Loaded(original)
        next.state = ReaderChapter.State.Loaded(original)
        val prevLoader = FakeLoader { prevFresh }
        val currLoader = FakeLoader { currFresh }
        val nextLoader = FakeLoader { nextFresh }
        prev.pageLoader = prevLoader
        curr.pageLoader = currLoader
        next.pageLoader = nextLoader

        reloadViewerChaptersForTranslationToggle(
            reloadChapter = reloader(),
            chapters = ViewerChapters(
                currChapter = curr,
                prevChapter = prev,
                nextChapter = next,
            ),
        ) shouldBe true

        prevLoader.calls shouldBe 1
        currLoader.calls shouldBe 1
        nextLoader.calls shouldBe 1
        curr.pages.shouldBeSameInstanceAs(currFresh)
        prev.pages.shouldBeSameInstanceAs(prevFresh)
        next.pages.shouldBeSameInstanceAs(nextFresh)
    }

    @Test
    fun `a failed current rebuild leaves the neighbours untouched`() = runTest {
        val original = listOf(readerPage(0))
        val prev = readerChapter()
        val curr = readerChapter()
        val next = readerChapter()
        curr.state = ReaderChapter.State.Loaded(original)
        prev.state = ReaderChapter.State.Loaded(original)
        next.state = ReaderChapter.State.Loaded(original)
        val prevLoader = FakeLoader { listOf(readerPage(1)) }
        val nextLoader = FakeLoader { listOf(readerPage(2)) }
        prev.pageLoader = prevLoader
        next.pageLoader = nextLoader
        curr.pageLoader = FakeLoader { emptyList() }

        reloadViewerChaptersForTranslationToggle(
            reloadChapter = reloader(),
            chapters = ViewerChapters(
                currChapter = curr,
                prevChapter = prev,
                nextChapter = next,
            ),
        ) shouldBe false

        prevLoader.calls shouldBe 0
        nextLoader.calls shouldBe 0
        prev.pages.shouldBeSameInstanceAs(original)
        next.pages.shouldBeSameInstanceAs(original)
    }

    @Test
    fun `a neighbour that is not loaded is left for ChapterLoader`() = runTest {
        val curr = readerChapter()
        val prev = readerChapter()
        val next = readerChapter()
        curr.state = ReaderChapter.State.Loaded(listOf(readerPage(0)))
        prev.state = ReaderChapter.State.Wait
        next.state = ReaderChapter.State.Error(mockk())
        val prevLoader = FakeLoader { listOf(readerPage(1)) }
        val nextLoader = FakeLoader { listOf(readerPage(2)) }
        prev.pageLoader = prevLoader
        next.pageLoader = nextLoader
        curr.pageLoader = FakeLoader { listOf(readerPage(3)) }

        reloadViewerChaptersForTranslationToggle(
            reloadChapter = reloader(),
            chapters = ViewerChapters(
                currChapter = curr,
                prevChapter = prev,
                nextChapter = next,
            ),
        ) shouldBe true

        prevLoader.calls shouldBe 0
        nextLoader.calls shouldBe 0
        prev.state shouldBe ReaderChapter.State.Wait
        next.state.shouldBeInstanceOf<ReaderChapter.State.Error>()
    }

    @Test
    fun `a neighbour rebuild failure keeps its old pages and does not revert the toggle`() = runTest {
        val curr = readerChapter()
        val next = readerChapter()
        val currFresh = listOf(readerPage(0))
        val nextOriginal = listOf(readerPage(9))
        curr.state = ReaderChapter.State.Loaded(listOf(readerPage(9)))
        next.state = ReaderChapter.State.Loaded(nextOriginal)
        curr.pageLoader = FakeLoader { currFresh }
        next.pageLoader = FakeLoader { throw RuntimeException("neighbour broke") }

        reloadViewerChaptersForTranslationToggle(
            reloadChapter = reloader(),
            chapters = ViewerChapters(
                currChapter = curr,
                prevChapter = null,
                nextChapter = next,
            ),
        ) shouldBe true

        curr.pages.shouldBeSameInstanceAs(currFresh)
        next.pages.shouldBeSameInstanceAs(nextOriginal)
    }

    @Test
    fun `a loading neighbour restarts so it reads the new preference`() = runTest {
        val curr = readerChapter()
        val next = readerChapter()
        val nextFresh = listOf(readerPage(1))
        curr.state = ReaderChapter.State.Loaded(listOf(readerPage(0)))
        curr.pageLoader = FakeLoader { listOf(readerPage(0)) }
        next.state = ReaderChapter.State.Loading
        val nextLoader = FakeLoader { nextFresh }
        next.pageLoader = nextLoader

        reloadViewerChaptersForTranslationToggle(
            reloadChapter = reloader(),
            chapters = ViewerChapters(
                currChapter = curr,
                prevChapter = null,
                nextChapter = next,
            ),
        ) shouldBe true

        nextLoader.calls shouldBe 1
        next.pages.shouldBeSameInstanceAs(nextFresh)
    }

    @Test
    fun `a toggle with no neighbours rebuilds the current chapter`() = runTest {
        val curr = readerChapter()
        val currFresh = listOf(readerPage(0))
        curr.state = ReaderChapter.State.Loaded(listOf(readerPage(9)))
        curr.pageLoader = FakeLoader { currFresh }

        reloadViewerChaptersForTranslationToggle(
            reloadChapter = reloader(),
            chapters = ViewerChapters(
                currChapter = curr,
                prevChapter = null,
                nextChapter = null,
            ),
        ) shouldBe true

        curr.pages.shouldBeSameInstanceAs(currFresh)
    }

    @Test
    fun `a neighbour reload cancelled by its loader does not stop the toggle`() = runTest {
        val curr = readerChapter()
        val prev = readerChapter()
        val currFresh = listOf(readerPage(1))
        val prevOriginal = listOf(readerPage(0))
        curr.state = ReaderChapter.State.Loaded(listOf(readerPage(0)))
        prev.state = ReaderChapter.State.Loaded(prevOriginal)
        curr.pageLoader = FakeLoader { currFresh }
        prev.pageLoader = FakeLoader { throw CancellationException("chapter released") }

        reloadViewerChaptersForTranslationToggle(
            reloadChapter = reloader(),
            chapters = ViewerChapters(
                currChapter = curr,
                prevChapter = prev,
                nextChapter = null,
            ),
        ) shouldBe true

        curr.pages.shouldBeSameInstanceAs(currFresh)
        prev.pages.shouldBeSameInstanceAs(prevOriginal)
    }

    @Test
    fun `a cancelled caller stops the toggle instead of reporting a failed reload`() = runTest {
        val curr = readerChapter()
        curr.state = ReaderChapter.State.Loaded(listOf(readerPage(0)))
        val reloadStarted = CompletableDeferred<Unit>()
        curr.pageLoader = object : PageLoader() {
            override var isLocal: Boolean = true

            override suspend fun getPages(): List<ReaderPage> {
                reloadStarted.complete(Unit)
                awaitCancellation()
            }
        }
        val outcome = CompletableDeferred<String>()
        val toggle = launch {
            try {
                val installed = reloadViewerChaptersForTranslationToggle(
                    reloadChapter = reloader(),
                    chapters = ViewerChapters(currChapter = curr, prevChapter = null, nextChapter = null),
                )
                outcome.complete("returned $installed")
            } catch (e: CancellationException) {
                outcome.complete("cancelled")
                throw e
            }
        }
        withContext(Dispatchers.Default) { withTimeout(5_000) { reloadStarted.await() } }

        toggle.cancelAndJoin()

        outcome.await() shouldBe "cancelled"
    }

    @Test
    fun `the current chapter of the viewer chapters is the one rebuilt first`() = runTest {
        val prev = readerChapter()
        val curr = readerChapter()
        val next = readerChapter()
        curr.state = ReaderChapter.State.Loaded(listOf(readerPage(0)))
        prev.state = ReaderChapter.State.Loaded(listOf(readerPage(0)))
        next.state = ReaderChapter.State.Loaded(listOf(readerPage(0)))
        val prevLoader = FakeLoader { listOf(readerPage(1)) }
        val nextLoader = FakeLoader { listOf(readerPage(2)) }
        prev.pageLoader = prevLoader
        next.pageLoader = nextLoader
        curr.pageLoader = FakeLoader { emptyList() }

        reloadViewerChaptersForTranslationToggle(
            reloadChapter = reloader(),
            chapters = ViewerChapters(
                currChapter = curr,
                prevChapter = prev,
                nextChapter = next,
            ),
        ) shouldBe false

        prevLoader.calls shouldBe 0
        nextLoader.calls shouldBe 0
    }

    @Test
    fun `no manga means no translation`() {
        computeHasTranslation(
            manga = null,
            chapter = Chapter.create().copy(name = "Chapter 1"),
            sourceManager = mockk(),
            translationStorageManager = mockk(),
            targetLanguage = "en",
        ) shouldBe false
    }

    @Test
    fun `the stored translation is looked up by chapter, manga title, source and language`() {
        val chapter = Chapter.create().copy(name = "Chapter 1", scanlator = "scan")
        val mangaSource = mockk<MangaSource>()
        val sourceManager = mockk<MangaSourceManager> {
            every { getOrStub(7L) } returns mangaSource
        }
        val storage = mockk<TranslationStorageManager> {
            every {
                isChapterTranslated("Chapter 1", "scan", "Manga", mangaSource, "es")
            } returns true
        }
        val manga = mockk<Manga> {
            every { title } returns "Manga"
            every { source } returns 7L
        }

        computeHasTranslation(manga, chapter, sourceManager, storage, "es") shouldBe true
        verify(exactly = 1) {
            storage.isChapterTranslated("Chapter 1", "scan", "Manga", mangaSource, "es")
        }
    }
}
