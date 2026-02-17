package eu.kanade.tachiyomi.ui.reader

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter

class ReaderChapterListManagerTest {

    private lateinit var getChaptersByMangaId: GetChaptersByMangaId
    private lateinit var downloadManager: MangaDownloadManager
    private lateinit var readerPreferences: ReaderPreferences
    private lateinit var basePreferences: BasePreferences
    private lateinit var manager: ReaderChapterListManager

    private val baseManga = Manga.create().copy(
        id = 1L,
        title = "Test Manga",
        source = 123L,
    )

    private val testChapters =
        listOf(chapter(id = 1L, number = 1.0), chapter(id = 2L, number = 2.0), chapter(id = 3L, number = 3.0))

    @BeforeEach
    fun setup() {
        getChaptersByMangaId = mockk()
        downloadManager = mockk(relaxed = true)
        readerPreferences = mockk(relaxed = true)
        basePreferences = mockk(relaxed = true)

        setSkipRead(false)
        setSkipFiltered(false)
        setSkipDupe(false)
        setDownloadedOnly(false)

        manager = ReaderChapterListManager(
            getChaptersByMangaId = getChaptersByMangaId,
            downloadManager = downloadManager,
            readerPreferences = readerPreferences,
            basePreferences = basePreferences,
        )
    }

    @Test
    fun `initChapterList should fetch chapters and initialize chapter list`() = runTest {
        // Given
        coEvery { getChaptersByMangaId.await(1L, applyScanlatorFilter = true) } returns testChapters
        manager.chapterId = 2L

        // When
        val result = manager.initChapterList(baseManga)

        // Then
        assertEquals(3, result.size)
        assertEquals(3, manager.chapterList.size)
        assertEquals("Chapter 1", manager.chapterList[0].chapter.name)
        assertEquals("Chapter 2", manager.chapterList[1].chapter.name)
        assertEquals("Chapter 3", manager.chapterList[2].chapter.name)
    }

    @Test
    fun `initChapterList should throw error when selected chapter not found`() = runTest {
        // Given
        coEvery { getChaptersByMangaId.await(1L, applyScanlatorFilter = true) } returns testChapters
        manager.chapterId = 999L // Non-existent chapter ID

        // When/Then
        try {
            manager.initChapterList(baseManga)
            fail("Expected IllegalStateException to be thrown")
        } catch (error: IllegalStateException) {
            assertEquals("Requested chapter of id 999 not found in chapter list", error.message)
        }
    }

    @Test
    fun `getChapterById should return chapter when present`() = runTest {
        initializeDefaultChapterList()

        val chapter = manager.getChapterById(2L)

        assertEquals(2L, chapter.chapter.id)
        assertEquals("Chapter 2", chapter.chapter.name)
    }

    @Test
    fun `getChapterById should throw when chapter is missing`() = runTest {
        initializeDefaultChapterList()

        assertThrows<NoSuchElementException> {
            manager.getChapterById(999L)
        }
    }

    @Test
    fun `getAdjacentChapters should return previous and next for middle chapter`() = runTest {
        initializeDefaultChapterList()
        val middle = manager.getChapterById(2L)

        val adjacent = manager.getAdjacentChapters(middle)

        assertEquals(1L, adjacent.previous?.chapter?.id)
        assertEquals(2L, adjacent.current.chapter.id)
        assertEquals(3L, adjacent.next?.chapter?.id)
    }

    @Test
    fun `getAdjacentChapters should return null previous at list start`() = runTest {
        initializeDefaultChapterList()
        val first = manager.getChapterById(1L)

        val adjacent = manager.getAdjacentChapters(first)

        assertNull(adjacent.previous)
        assertEquals(2L, adjacent.next?.chapter?.id)
    }

    @Test
    fun `getAdjacentChapters should return null next at list end`() = runTest {
        initializeDefaultChapterList()
        val last = manager.getChapterById(3L)

        val adjacent = manager.getAdjacentChapters(last)

        assertEquals(2L, adjacent.previous?.chapter?.id)
        assertNull(adjacent.next)
    }

    @Test
    fun `getChapterForDeletion should return chapter based on configured slots`() = runTest {
        initializeDefaultChapterList()
        val current = manager.getChapterById(3L)

        val chapterForDeletion = manager.getChapterForDeletion(current, removeAfterReadSlots = 1)

        assertEquals(2L, chapterForDeletion?.chapter?.id)
    }

    @Test
    fun `getChapterForDeletion should return null when target is out of bounds`() = runTest {
        initializeDefaultChapterList()
        val current = manager.getChapterById(2L)

        val chapterForDeletion = manager.getChapterForDeletion(current, removeAfterReadSlots = 3)

        assertNull(chapterForDeletion)
    }

    @Test
    fun `getDuplicateUnreadChapterUpdates should include only unread recognized matching chapter numbers`() = runTest {
        val chapters = listOf(
            chapter(id = 1L, number = 1.0),
            chapter(id = 2L, number = 2.0, read = false),
            chapter(id = 3L, number = 2.0, read = true),
            chapter(id = 4L, number = 2.0, read = false),
            chapter(id = 5L, number = -1.0, read = false),
        )

        initializeChapterList(chapters, selectedChapterId = 2L)
        val selected = manager.getChapterById(2L)

        val updates = manager.getDuplicateUnreadChapterUpdates(selected)

        assertEquals(listOf(2L, 4L), updates.map { it.id })
        assertEquals(listOf(true, true), updates.map { it.read })
    }

    @Test
    fun `initChapterList should apply read and manga filters and still keep selected chapter`() = runTest {
        setSkipRead(true)
        setSkipFiltered(true)

        every {
            downloadManager.isChapterDownloaded(any(), any(), any(), any())
        } answers {
            firstArg<String>() in setOf("Chapter 2", "Chapter 3")
        }

        val mangaWithFilters = baseManga.copy(
            chapterFlags = Manga.CHAPTER_SHOW_UNREAD or
                Manga.CHAPTER_SHOW_DOWNLOADED or
                Manga.CHAPTER_SHOW_BOOKMARKED,
        )

        val chapters = listOf(
            chapter(id = 1L, number = 1.0, read = true, bookmark = false), // selected; filtered but kept
            chapter(id = 2L, number = 2.0, read = false, bookmark = true), // passes all filters
            chapter(id = 3L, number = 3.0, read = false, bookmark = false), // filtered by bookmark
            chapter(id = 4L, number = 4.0, read = false, bookmark = true), // filtered by downloaded
        )

        initializeChapterList(chapters, selectedChapterId = 1L, manga = mangaWithFilters)

        assertEquals(setOf(1L, 2L), manager.chapterList.mapNotNull { it.chapter.id }.toSet())
    }

    private suspend fun initializeDefaultChapterList() {
        initializeChapterList(testChapters, selectedChapterId = 2L)
    }

    private suspend fun initializeChapterList(
        chapters: List<Chapter>,
        selectedChapterId: Long,
        manga: Manga = baseManga,
    ) {
        coEvery { getChaptersByMangaId.await(manga.id, applyScanlatorFilter = true) } returns chapters
        manager.chapterId = selectedChapterId
        manager.initChapterList(manga)
    }

    private fun setSkipRead(enabled: Boolean) {
        every { readerPreferences.skipRead() } returns booleanPreference(enabled)
    }

    private fun setSkipFiltered(enabled: Boolean) {
        every { readerPreferences.skipFiltered() } returns booleanPreference(enabled)
    }

    private fun setSkipDupe(enabled: Boolean) {
        every { readerPreferences.skipDupe() } returns booleanPreference(enabled)
    }

    private fun setDownloadedOnly(enabled: Boolean) {
        every { basePreferences.downloadedOnly() } returns booleanPreference(enabled)
    }

    private fun booleanPreference(value: Boolean): Preference<Boolean> {
        return mockk {
            every { get() } returns value
        }
    }

    private fun chapter(
        id: Long,
        number: Double,
        read: Boolean = false,
        bookmark: Boolean = false,
    ): Chapter {
        return Chapter.create().copy(
            id = id,
            mangaId = baseManga.id,
            name = "Chapter $id",
            chapterNumber = number,
            read = read,
            bookmark = bookmark,
        )
    }
}
