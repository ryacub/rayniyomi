package eu.kanade.tachiyomi.util.chapter

import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

class ChapterResumeTest {

    private val previousGraph = AppGraphHolder.graphOrNull

    @BeforeEach
    fun setUp() {
        AppGraphHolder.graph = mockk<AppGraph> {
            every { basePreferences.downloadedOnly().get() } returns false
        }
    }

    @AfterEach
    fun tearDown() {
        AppGraphHolder.graphOrNull = previousGraph
    }

    @Test
    fun `untouched title has nothing to resume`() {
        val items = listOf(item(1.0), item(2.0))

        assertNull(items.resumeChapter(manga(Manga.CHAPTER_SORT_ASC)))
    }

    @Test
    fun `partly read chapter is the one to resume`() {
        val items = listOf(item(1.0, lastPageRead = 11L), item(2.0))

        assertEquals(1.0, items.resumeChapter(manga(Manga.CHAPTER_SORT_ASC))?.chapterNumber)
    }

    @Test
    fun `finished chapter resumes at the next chapter`() {
        val items = listOf(item(1.0, read = true), item(2.0))

        assertEquals(2.0, items.resumeChapter(manga(Manga.CHAPTER_SORT_ASC))?.chapterNumber)
    }

    @Test
    fun `descending sort still resumes the lowest unread chapter`() {
        val items = listOf(item(3.0), item(2.0, lastPageRead = 4L), item(1.0, read = true))

        assertEquals(2.0, items.resumeChapter(manga(Manga.CHAPTER_SORT_DESC))?.chapterNumber)
    }

    @Test
    fun `unread filter that hides read chapters keeps the chapter to resume`() {
        val items = listOf(item(1.0, read = true), item(2.0, lastPageRead = 2L))
        val manga = manga(Manga.CHAPTER_SORT_ASC or Manga.CHAPTER_SHOW_UNREAD)

        assertEquals(2.0, items.resumeChapter(manga)?.chapterNumber)
    }

    @Test
    fun `fully read title has nothing to resume`() {
        val items = listOf(item(1.0, read = true), item(2.0, read = true))

        assertNull(items.resumeChapter(manga(Manga.CHAPTER_SORT_ASC)))
    }

    @Test
    fun `untouched title starts for the first time`() {
        val items = listOf(item(1.0), item(2.0))

        assertEquals(
            StartAction.FirstStart(items[0].chapter),
            items.startAction(manga(Manga.CHAPTER_SORT_ASC)),
        )
    }

    @Test
    fun `progress on a later chapter only resumes instead of starting for the first time`() {
        val items = listOf(item(1.0), item(2.0, lastPageRead = 3L))

        assertEquals(
            StartAction.Resume(items[0].chapter),
            items.startAction(manga(Manga.CHAPTER_SORT_ASC)),
        )
    }

    @Test
    fun `fully read title has no start action`() {
        val items = listOf(item(1.0, read = true), item(2.0, read = true))

        assertEquals(StartAction.None, items.startAction(manga(Manga.CHAPTER_SORT_ASC)))
    }

    private fun manga(flags: Long) = Manga.create().copy(
        id = 1L,
        chapterFlags = flags or Manga.CHAPTER_SORTING_NUMBER,
    )

    private fun item(number: Double, read: Boolean = false, lastPageRead: Long = 0L) = ChapterList.Item(
        chapter = Chapter.create().copy(
            id = number.toLong(),
            mangaId = 1L,
            chapterNumber = number,
            read = read,
            lastPageRead = lastPageRead,
        ),
        downloadState = MangaDownload.State.NOT_DOWNLOADED,
        downloadProgress = 0,
    )
}
