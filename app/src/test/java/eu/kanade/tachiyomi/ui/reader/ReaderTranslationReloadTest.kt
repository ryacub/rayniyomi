package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import io.kotest.matchers.shouldBe
import io.mockk.mockk
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
}
