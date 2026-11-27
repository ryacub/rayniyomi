package tachiyomi.data.entries.manga

import app.cash.sqldelight.db.QueryResult
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryMangaDb
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import tachiyomi.domain.entries.manga.model.LinkMangaSourcesResult
import tachiyomi.domain.entries.manga.model.MangaSourceLink

class ClearDatabaseLinkedMangaTest {

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `cleanup keeps an unsaved manga linked to a saved one`(savedFirst: Boolean) = runTest {
        InMemoryMangaDb().use { db ->
            db.insertLibraryFixture()
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            val result = if (savedFirst) repository.link(A, B) else repository.link(B, A)
            assertEquals(LinkMangaSourcesResult.Linked, result)

            db.clearDatabase(listOf(SOURCE_TWO))

            assertEquals(listOf(A, B, S), db.mangaIds())
            assertEquals(listOf(Triple(1L, 1L, 5L)), db.chapterState(B))
            assertEquals(listOf(Triple(1L, 1L, 1000L)), db.historyRows())
            val link = if (savedFirst) MangaSourceLink(A, B) else MangaSourceLink(B, A)
            assertEquals(link, repository.getForManga(A))
            assertEquals(link, repository.getForManga(B))
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `removable counts exclude protected manga`(savedFirst: Boolean) = runTest {
        InMemoryMangaDb().use { db ->
            db.insertLibraryFixture()
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            val result = if (savedFirst) repository.link(A, B) else repository.link(B, A)
            assertEquals(LinkMangaSourcesResult.Linked, result)

            assertEquals(mapOf(SOURCE_TWO to 1L), db.removableCounts())
        }
    }

    @Test
    fun `a source with only protected manga has no cleanup row`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "saved", title = "Saved", source = 1)
            db.insertManga(id = 2, url = "alternate", title = "Alternate", source = 3, favorite = false)
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            assertEquals(LinkMangaSourcesResult.Linked, repository.link(1, 2))

            assertEquals(emptyMap<Long, Long>(), db.removableCounts())
        }
    }

    @Test
    fun `unlinked alternate becomes removable after unlink`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertLibraryFixture()
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            assertEquals(LinkMangaSourcesResult.Linked, repository.link(A, B))
            assertEquals(true, repository.unlink(A, B))

            assertEquals(mapOf(SOURCE_TWO to 2L), db.removableCounts())

            db.clearDatabase(listOf(SOURCE_TWO))

            assertEquals(listOf(A, S), db.mangaIds())
        }
    }

    @Test
    fun `two unsaved linked manga are removable`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 5, url = "e", title = "E", source = 4, favorite = false)
            db.insertManga(id = 6, url = "f", title = "F", source = 5, favorite = false)
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            assertEquals(LinkMangaSourcesResult.Linked, repository.link(5, 6))

            assertEquals(mapOf(4L to 1L, 5L to 1L), db.removableCounts())

            db.clearDatabase(listOf(4L, 5L))

            assertEquals(emptyList<Long>(), db.mangaIds())
            assertEquals(emptyList<MangaSourceLink>(), repository.getAll())
        }
    }

    private fun InMemoryMangaDb.insertLibraryFixture() {
        insertManga(id = A, url = "a", title = "A", source = 1)
        insertManga(id = B, url = "b", title = "B", source = SOURCE_TWO, favorite = false)
        insertManga(id = C, url = "c", title = "C", source = SOURCE_TWO, favorite = false)
        insertManga(id = S, url = "s", title = "S", source = SOURCE_TWO)
        insertChapter(id = 1, mangaId = B, url = "b1")
        insertChapter(id = 2, mangaId = B, url = "b2")
        driver.execute(null, "UPDATE chapters SET read = 1, bookmark = 1, last_page_read = 5 WHERE _id = 1", 0)
        insertHistory(id = 1, chapterId = 1, lastRead = 1000)
        insertHistory(id = 2, chapterId = 2, lastRead = 0)
    }

    private suspend fun InMemoryMangaDb.removableCounts(): Map<Long, Long> {
        return handler.await { mangasQueries.getSourceIdsWithNonLibraryManga().executeAsList() }
            .associate { it.source to it.manga_count }
    }

    private suspend fun InMemoryMangaDb.clearDatabase(sourceIds: List<Long>) {
        handler.await {
            mangasQueries.deleteMangasNotInLibraryBySourceIds(sourceIds)
            historyQueries.removeResettedHistory()
        }
    }

    private fun InMemoryMangaDb.mangaIds(): List<Long> {
        val ids = mutableListOf<Long>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT _id FROM mangas ORDER BY _id",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) ids += cursor.getLong(0)!!
                QueryResult.Unit
            },
        )
        return ids
    }

    private fun InMemoryMangaDb.chapterState(mangaId: Long): List<Triple<Long, Long, Long>> {
        val rows = mutableListOf<Triple<Long, Long, Long>>()
        driver.executeQuery(
            identifier = null,
            sql = "SELECT read, bookmark, last_page_read FROM chapters WHERE manga_id = $mangaId AND read = 1",
            parameters = 0,
            mapper = { cursor ->
                while (cursor.next().value) {
                    rows += Triple(cursor.getLong(0)!!, cursor.getLong(1)!!, cursor.getLong(2)!!)
                }
                QueryResult.Unit
            },
        )
        return rows
    }

    private companion object {
        const val A = 1L
        const val B = 2L
        const val C = 3L
        const val S = 4L
        const val SOURCE_TWO = 2L
    }
}
