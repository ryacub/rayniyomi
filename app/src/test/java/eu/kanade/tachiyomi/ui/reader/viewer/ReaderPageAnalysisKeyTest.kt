package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ReaderPageAnalysisKeyTest {

    private val config = mockk<ViewerConfig>(relaxed = true)

    @Test
    fun `pages without a URL in one chapter get different keys`() {
        val chapter = readerChapter(id = 7L)

        val first = readerPageAnalysisKey(localPage(index = 0, chapter), config)
        val second = readerPageAnalysisKey(localPage(index = 1, chapter), config)

        first shouldNotBe second
    }

    @Test
    fun `pages without a URL at one index in two chapters get different keys`() {
        val first = readerPageAnalysisKey(localPage(index = 0, readerChapter(id = 7L)), config)
        val second = readerPageAnalysisKey(localPage(index = 0, readerChapter(id = 8L)), config)

        first shouldNotBe second
    }

    @Test
    fun `an HTTP page key starts with its image URL`() {
        val page = ReaderPage(0, url = "https://example.org/1", imageUrl = "https://cdn.example.org/1.png")
        page.chapter = readerChapter(id = 7L)

        readerPageAnalysisKey(page, config) shouldStartWith "https://cdn.example.org/1.png|"
    }

    @Test
    fun `a viewer flag changes the key`() {
        val page = localPage(index = 0, readerChapter(id = 7L))

        val parent = readerPageAnalysisKey(page, config, "insert" to false)
        val insert = readerPageAnalysisKey(page, config, "insert" to true)

        parent shouldNotBe insert
    }

    private fun readerChapter(id: Long) = ReaderChapter(ChapterImpl(id))

    private fun localPage(index: Int, chapter: ReaderChapter) = ReaderPage(index).apply {
        this.chapter = chapter
    }
}
