package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupHistory
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga

/**
 * R795: two library entries can hold the same chapter URL. The restore must resolve each
 * history record inside its owning manga.
 */
class MangaRestorerHistoryTest {

    private fun restorer(db: InMemoryMangaDb) = MangaRestorer(
        handler = db.handler,
        getCategories = mockk(relaxed = true),
        getMangaByUrlAndSourceId = mockk(relaxed = true),
        getChaptersByMangaId = mockk(relaxed = true),
        updateManga = mockk(relaxed = true),
        getTracks = mockk(relaxed = true),
        insertTrack = mockk(relaxed = true),
        fetchInterval = mockk(relaxed = true),
    )

    private fun manga(id: Long) = Manga.create().copy(id = id)

    @Test
    fun `restore adds history to the owning manga when another manga shares the chapter url`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "manga-a", title = "A")
            db.insertManga(id = 2, url = "manga-b", title = "B")
            db.insertChapter(id = 10, mangaId = 1, url = "shared-chapter")
            db.insertChapter(id = 20, mangaId = 2, url = "shared-chapter")

            // Manga 2 already has history. Manga 1 has none.
            db.insertHistory(id = 100, chapterId = 20, lastRead = 5_000)

            restorer(db).restoreHistory(
                manga(1),
                listOf(BackupHistory(url = "shared-chapter", lastRead = 9_000)),
            )

            val rows = db.historyRows()
            assertEquals(2, rows.size, "the restore must add history instead of touching manga 2")

            val manga2History = rows.single { it.first == 100L }
            assertEquals(20L, manga2History.second)
            assertEquals(5_000L, manga2History.third, "manga 2 history must not change")

            val manga1History = rows.single { it.first != 100L }
            assertEquals(10L, manga1History.second, "history must attach to the manga 1 chapter")
            assertEquals(9_000L, manga1History.third)
        }
    }

    @Test
    fun `restore updates existing history only within the owning manga`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "manga-a", title = "A")
            db.insertManga(id = 2, url = "manga-b", title = "B")
            db.insertChapter(id = 10, mangaId = 1, url = "shared-chapter")
            db.insertChapter(id = 20, mangaId = 2, url = "shared-chapter")
            db.insertHistory(id = 100, chapterId = 10, lastRead = 1_000)
            db.insertHistory(id = 200, chapterId = 20, lastRead = 5_000)

            restorer(db).restoreHistory(
                manga(1),
                listOf(BackupHistory(url = "shared-chapter", lastRead = 9_000)),
            )

            val rows = db.historyRows()
            assertEquals(2, rows.size)
            assertEquals(9_000L, rows.single { it.first == 100L }.third, "manga 1 history advances")
            assertEquals(5_000L, rows.single { it.first == 200L }.third, "manga 2 history is untouched")
        }
    }

    @Test
    fun `restore tolerates a duplicated chapter url inside one manga`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "manga-a", title = "A")
            // The same URL twice under ONE manga - scoping alone does not make this single-row.
            db.insertChapter(id = 10, mangaId = 1, url = "dupe")
            db.insertChapter(id = 11, mangaId = 1, url = "dupe")

            restorer(db).restoreHistory(
                manga(1),
                listOf(BackupHistory(url = "dupe", lastRead = 9_000)),
            )

            assertEquals(1, db.historyRows().size)
        }
    }
}
