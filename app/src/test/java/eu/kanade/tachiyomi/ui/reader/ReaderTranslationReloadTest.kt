package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.loader.PageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class ReaderTranslationReloadTest {

    private fun readerChapter(lastPageRead: Int = 0): ReaderChapter {
        val chapter = ChapterImpl().apply {
            id = 1L
            manga_id = 1L
            name = "Chapter 1"
            last_page_read = lastPageRead
        }
        return ReaderChapter(chapter)
    }

    @Test
    fun `no reload is requested while translated pages are hidden`() {
        val chapter = readerChapter(lastPageRead = 7)
        chapter.state = ReaderChapter.State.Loaded(emptyList())

        prepareTranslationReload(chapter, showTranslatedPages = false) shouldBe false
    }

    @Test
    fun `a hidden translation leaves the loaded pages untouched`() {
        val chapter = readerChapter(lastPageRead = 7)
        val loaded = ReaderChapter.State.Loaded(emptyList())
        chapter.state = loaded
        chapter.requestedPage = 3

        prepareTranslationReload(chapter, showTranslatedPages = false)

        chapter.state shouldBe loaded
        chapter.requestedPage shouldBe 3
    }

    @Test
    fun `a shown translation resets the chapter so the loader rebuilds its pages`() {
        val chapter = readerChapter(lastPageRead = 7)
        chapter.state = ReaderChapter.State.Loaded(emptyList())

        prepareTranslationReload(chapter, showTranslatedPages = true) shouldBe true

        chapter.state shouldBe ReaderChapter.State.Wait
    }

    @Test
    fun `the read position survives the reload`() {
        val chapter = readerChapter(lastPageRead = 7)
        chapter.state = ReaderChapter.State.Loaded(emptyList())
        chapter.requestedPage = 0

        prepareTranslationReload(chapter, showTranslatedPages = true)

        chapter.requestedPage shouldBe 7
    }

    @Test
    fun `a chapter still loading is also reset`() {
        val chapter = readerChapter(lastPageRead = 2)
        chapter.state = ReaderChapter.State.Loading

        prepareTranslationReload(chapter, showTranslatedPages = true) shouldBe true

        chapter.state shouldBe ReaderChapter.State.Wait
        chapter.requestedPage shouldBe 2
    }

    @Test
    fun `an errored chapter is reset rather than left showing the old-language failure`() {
        val chapter = readerChapter(lastPageRead = 4)
        chapter.state = ReaderChapter.State.Error(mockk())

        prepareTranslationReload(chapter, showTranslatedPages = true) shouldBe true

        chapter.state shouldBe ReaderChapter.State.Wait
    }

    private fun readerPage(index: Int): ReaderPage = ReaderPage(index)

    private class FakeLoader(val action: () -> List<ReaderPage>) : PageLoader() {
        var calls = 0

        override var isLocal: Boolean = true

        override suspend fun getPages(): List<ReaderPage> {
            calls++
            return action()
        }
    }

    @Test
    fun `a rebuild consults the loader again and installs a fresh loaded state`() = runTest {
        val chapter = readerChapter(lastPageRead = 7)
        val original = listOf(readerPage(0), readerPage(1))
        val translated = listOf(readerPage(0), readerPage(1))
        chapter.state = ReaderChapter.State.Loaded(original)
        val loader = FakeLoader { translated }
        chapter.pageLoader = loader

        reloadChapterPagesForTranslationToggle(chapter) shouldBe true

        loader.calls shouldBe 1
        chapter.pages.shouldBeSameInstanceAs(translated)
        translated.forEach { it.chapter shouldBe chapter }
    }

    @Test
    fun `a chapter without a page loader is left untouched`() = runTest {
        val chapter = readerChapter()
        val loaded = ReaderChapter.State.Loaded(listOf(readerPage(0)))
        chapter.state = loaded

        reloadChapterPagesForTranslationToggle(chapter) shouldBe false

        chapter.state shouldBe loaded
    }

    @Test
    fun `a chapter still loading is left to its in-flight load`() = runTest {
        val chapter = readerChapter()
        chapter.state = ReaderChapter.State.Loading
        val loader = FakeLoader { emptyList() }
        chapter.pageLoader = loader

        reloadChapterPagesForTranslationToggle(chapter) shouldBe false

        loader.calls shouldBe 0
        chapter.state shouldBe ReaderChapter.State.Loading
    }

    @Test
    fun `a waiting chapter is rebuilt into a fresh loaded state`() = runTest {
        val chapter = readerChapter()
        chapter.state = ReaderChapter.State.Wait
        val pages = listOf(readerPage(0))
        chapter.pageLoader = FakeLoader { pages }

        reloadChapterPagesForTranslationToggle(chapter) shouldBe true

        chapter.pages shouldBe pages
    }

    @Test
    fun `an errored chapter is rebuilt into a fresh loaded state`() = runTest {
        val chapter = readerChapter()
        chapter.state = ReaderChapter.State.Error(mockk())
        val pages = listOf(readerPage(0))
        chapter.pageLoader = FakeLoader { pages }

        reloadChapterPagesForTranslationToggle(chapter) shouldBe true

        chapter.pages shouldBe pages
    }

    @Test
    fun `an empty rebuild installs an error and reports failure`() = runTest {
        val chapter = readerChapter()
        chapter.state = ReaderChapter.State.Loaded(listOf(readerPage(0)))
        chapter.pageLoader = FakeLoader { emptyList() }

        reloadChapterPagesForTranslationToggle(chapter) shouldBe false

        chapter.state.shouldBeInstanceOf<ReaderChapter.State.Error>()
    }

    @Test
    fun `a loader failure installs an error instead of crashing the toggle`() = runTest {
        val chapter = readerChapter()
        val error = RuntimeException("loader broke")
        chapter.state = ReaderChapter.State.Loaded(listOf(readerPage(0)))
        chapter.pageLoader = FakeLoader { throw error }

        reloadChapterPagesForTranslationToggle(chapter) shouldBe false

        chapter.state shouldBe ReaderChapter.State.Error(error)
    }

    @Test
    fun `cancellation from the loader propagates instead of being swallowed`() = runTest {
        val chapter = readerChapter()
        val loaded = ReaderChapter.State.Loaded(listOf(readerPage(0)))
        chapter.state = loaded
        chapter.pageLoader = FakeLoader { throw CancellationException("toggle cancelled") }

        val result = runCatching { reloadChapterPagesForTranslationToggle(chapter) }

        result.exceptionOrNull().shouldBeInstanceOf<CancellationException>()
        chapter.state shouldBe loaded
    }
}
