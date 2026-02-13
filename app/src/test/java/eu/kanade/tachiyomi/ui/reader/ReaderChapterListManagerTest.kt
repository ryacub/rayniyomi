package eu.kanade.tachiyomi.ui.reader

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter

class ReaderChapterListManagerTest {

    private lateinit var getChaptersByMangaId: GetChaptersByMangaId
    private lateinit var downloadManager: MangaDownloadManager
    private lateinit var readerPreferences: ReaderPreferences
    private lateinit var basePreferences: BasePreferences
    private lateinit var manager: ReaderChapterListManager

    private val testManga = Manga.create().copy(
        id = 1L,
        title = "Test Manga",
        source = 123L,
    )

    private val testChapters = listOf(
        Chapter.create().copy(
            id = 1L,
            mangaId = 1L,
            name = "Chapter 1",
            chapterNumber = 1.0,
            read = false,
        ),
        Chapter.create().copy(
            id = 2L,
            mangaId = 1L,
            name = "Chapter 2",
            chapterNumber = 2.0,
            read = false,
        ),
        Chapter.create().copy(
            id = 3L,
            mangaId = 1L,
            name = "Chapter 3",
            chapterNumber = 3.0,
            read = false,
        ),
    )

    @BeforeEach
    fun setup() {
        getChaptersByMangaId = mockk()
        downloadManager = mockk(relaxed = true)
        readerPreferences = mockk(relaxed = true)
        basePreferences = mockk(relaxed = true)

        // Mock preference getters
        coEvery { readerPreferences.skipRead() } returns mockk {
            coEvery { get() } returns false
        }
        coEvery { readerPreferences.skipFiltered() } returns mockk {
            coEvery { get() } returns false
        }
        coEvery { readerPreferences.skipDupe() } returns mockk {
            coEvery { get() } returns false
        }
        coEvery { basePreferences.downloadedOnly() } returns mockk {
            coEvery { get() } returns false
        }

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
        val result = manager.initChapterList(testManga)

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
            manager.initChapterList(testManga)
            throw AssertionError("Expected error to be thrown")
        } catch (e: IllegalStateException) {
            assertEquals("Requested chapter of id 999 not found in chapter list", e.message)
        }
    }
}
