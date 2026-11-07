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

class ChapterSavedPositionTest {

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
    fun `untouched title has no saved position`() {
        val items = listOf(item(1.0), item(2.0))

        assertNull(items.savedReadingPosition(manga(Manga.CHAPTER_SORT_ASC)))
    }

    @Test
    fun `partly read chapter gives its one-based page`() {
        val items = listOf(item(1.0, lastPageRead = 11L), item(2.0))

        val position = items.savedReadingPosition(manga(Manga.CHAPTER_SORT_ASC))

        assertEquals(1.0, position?.chapter?.chapterNumber)
        assertEquals(12, position?.page)
    }

    @Test
    fun `finished chapter gives the next chapter without a page`() {
        val items = listOf(item(1.0, read = true), item(2.0))

        val position = items.savedReadingPosition(manga(Manga.CHAPTER_SORT_ASC))

        assertEquals(2.0, position?.chapter?.chapterNumber)
        assertNull(position?.page)
    }

    @Test
    fun `descending sort still names the lowest unread chapter`() {
        val items = listOf(item(3.0), item(2.0, lastPageRead = 4L), item(1.0, read = true))

        val position = items.savedReadingPosition(manga(Manga.CHAPTER_SORT_DESC))

        assertEquals(2.0, position?.chapter?.chapterNumber)
        assertEquals(5, position?.page)
    }

    @Test
    fun `unread filter that hides read chapters keeps the saved position`() {
        val items = listOf(item(1.0, read = true), item(2.0, lastPageRead = 2L))
        val manga = manga(Manga.CHAPTER_SORT_ASC or Manga.CHAPTER_SHOW_UNREAD)

        val position = items.savedReadingPosition(manga)

        assertEquals(2.0, position?.chapter?.chapterNumber)
        assertEquals(3, position?.page)
    }

    @Test
    fun `fully read title has no saved position`() {
        val items = listOf(item(1.0, read = true), item(2.0, read = true))

        assertNull(items.savedReadingPosition(manga(Manga.CHAPTER_SORT_ASC)))
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
