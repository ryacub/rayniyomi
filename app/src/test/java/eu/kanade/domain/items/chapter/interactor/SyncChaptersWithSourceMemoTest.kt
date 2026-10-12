package eu.kanade.domain.items.chapter.interactor

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SChapter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.chapter.model.ChapterUpdate
import tachiyomi.domain.items.chapter.repository.ChapterRepository
import tachiyomi.domain.library.service.LibraryPreferences

class SyncChaptersWithSourceMemoTest {

    @Test
    fun `a changed memo on a stored chapter is written`() = runTest {
        val newMemo = JsonObject(mapOf("mangaSlug" to JsonPrimitive("series-9z")))
        // Memo is the only field that differs from the source chapter.
        val stored = Chapter.create().copy(
            id = 5,
            mangaId = 1,
            url = "/chapter/1",
            name = "Chapter 1",
            chapterNumber = 1.0,
            dateUpload = 0,
        )
        val remote = SChapter.create().apply {
            url = "/chapter/1"
            name = "Chapter 1"
            chapter_number = 1f
            memo = newMemo
        }
        val chapterRepository = mockk<ChapterRepository>()
        val updates = slot<List<ChapterUpdate>>()
        coEvery { chapterRepository.syncChapters(any(), any(), capture(updates)) } returns emptyList()
        val sync = SyncChaptersWithSource(
            downloadManager = mockk(relaxed = true),
            downloadProvider = mockk(relaxed = true),
            chapterRepository = chapterRepository,
            shouldUpdateDbChapter = ShouldUpdateDbChapter(),
            updateManga = mockk(relaxed = true),
            getChaptersByMangaId = mockk<GetChaptersByMangaId> { coEvery { await(1, any()) } returns listOf(stored) },
            getExcludedScanlators = mockk(relaxed = true),
            libraryPreferences = mockk<LibraryPreferences> {
                every { markDuplicateReadChapterAsRead().get() } returns emptySet()
            },
        )

        sync.await(
            listOf(remote),
            Manga.create().copy(id = 1, title = "Series"),
            mockk<MangaSource> {
                every { id } returns
                    1
            },
        )

        coVerify(exactly = 1) { chapterRepository.syncChapters(emptyList(), emptyList(), any()) }
        assertEquals(newMemo, updates.captured.single().memo)
    }
}
