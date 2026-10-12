package eu.kanade.domain.items.chapter.interactor

import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryMangaDb
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.SChapter
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.items.chapter.ChapterRepositoryImpl
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.ShouldUpdateDbChapter
import tachiyomi.domain.library.service.LibraryPreferences

class SyncChaptersWithSourceAtomicTest {

    @Test
    fun `a failed insert keeps the chapters that the sync was replacing`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "/series", title = "Series")
            db.insertChapter(id = 10, mangaId = 1, url = "/old")
            db.driver.execute(null, "UPDATE chapters SET read = 1, bookmark = 1 WHERE _id = 10", 0)
            db.driver.execute(
                null,
                "CREATE TRIGGER fail_insert BEFORE INSERT ON chapters WHEN NEW.url = '/new' " +
                    "BEGIN SELECT RAISE(ABORT, 'insert failed'); END",
                0,
            )
            val repository = ChapterRepositoryImpl(db.handler)

            val result = runCatching { syncRenamedChapter(repository) }

            val stored = repository.getChapterByMangaId(1)
            assertEquals(listOf("/old"), stored.map { it.url })
            assertTrue(stored.single().read && stored.single().bookmark)
            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `a renamed chapter replaces the old row and keeps its read state`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "/series", title = "Series")
            db.insertChapter(id = 10, mangaId = 1, url = "/old")
            db.driver.execute(null, "UPDATE chapters SET read = 1 WHERE _id = 10", 0)
            val repository = ChapterRepositoryImpl(db.handler)

            syncRenamedChapter(repository)

            val stored = repository.getChapterByMangaId(1).single()
            assertEquals("/new", stored.url)
            assertTrue(stored.read && stored.id > 0)
        }
    }

    private suspend fun syncRenamedChapter(repository: ChapterRepositoryImpl) = SyncChaptersWithSource(
        downloadManager = mockk(relaxed = true),
        downloadProvider = mockk(relaxed = true),
        chapterRepository = repository,
        shouldUpdateDbChapter = ShouldUpdateDbChapter(),
        updateManga = mockk(relaxed = true),
        getChaptersByMangaId = GetChaptersByMangaId(repository),
        getExcludedScanlators = mockk(relaxed = true),
        libraryPreferences = mockk<LibraryPreferences> {
            every { markDuplicateReadChapterAsRead().get() } returns emptySet()
        },
    ).await(
        listOf(
            SChapter.create().apply {
                url = "/new"
                name = "Chapter 1"
                chapter_number = 1f
            },
        ),
        Manga.create().copy(id = 1, title = "Series"),
        mockk<MangaSource> { every { id } returns 1 },
    )
}
