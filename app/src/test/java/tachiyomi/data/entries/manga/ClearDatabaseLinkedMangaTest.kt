package tachiyomi.data.entries.manga

import app.cash.sqldelight.db.QueryResult
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryMangaDb
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import tachiyomi.domain.entries.manga.model.LinkMangaSourcesResult
import tachiyomi.domain.entries.manga.model.MangaSourceLink

class ClearDatabaseLinkedMangaTest {

    @ParameterizedTest
    @CsvSource("true,false", "false,false", "true,true", "false,true")
    fun `cleanup keeps an unsaved manga linked to a saved one`(savedFirst: Boolean, savedHasLargerId: Boolean) =
        runTest {
            val a = if (savedHasLargerId) A_LARGE else A_SMALL
            InMemoryMangaDb().use { db ->
                db.insertLibraryFixture(a)
                val repository = MangaSourceLinkRepositoryImpl(db.handler)
                assertEquals(LinkMangaSourcesResult.Linked, repository.linkSavedAndAlternate(a, savedFirst))

                db.clearDatabase(listOf(SOURCE_TWO))

                assertEquals(listOf(a, B, S, D).sorted(), db.mangaIds())
                assertEquals(listOf(Triple(1L, 1L, 5L), Triple(0L, 0L, 0L)), db.chapterState(B))
                assertEquals(listOf(Triple(1L, 1L, 1000L)), db.historyRows())
                val link = if (savedFirst) MangaSourceLink(a, B) else MangaSourceLink(B, a)
                assertEquals(link, repository.getForManga(a))
                assertEquals(link, repository.getForManga(B))
            }
        }

    @ParameterizedTest
    @CsvSource("true,false", "false,false", "true,true", "false,true")
    fun `removable counts exclude protected manga`(savedFirst: Boolean, savedHasLargerId: Boolean) = runTest {
        val a = if (savedHasLargerId) A_LARGE else A_SMALL
        InMemoryMangaDb().use { db ->
            db.insertLibraryFixture(a)
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            assertEquals(LinkMangaSourcesResult.Linked, repository.linkSavedAndAlternate(a, savedFirst))

            assertEquals(mapOf(SOURCE_TWO to 1L, SOURCE_THREE to 1L), db.removableCounts())
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
            db.insertLibraryFixture(A_SMALL)
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            assertEquals(LinkMangaSourcesResult.Linked, repository.link(A_SMALL, B))
            assertEquals(true, repository.unlink(A_SMALL, B))

            assertEquals(mapOf(SOURCE_TWO to 2L, SOURCE_THREE to 1L), db.removableCounts())

            db.clearDatabase(listOf(SOURCE_TWO))

            assertEquals(listOf(A_SMALL, S, D), db.mangaIds())
        }
    }

    @Test
    fun `linked manga becomes removable after its partner leaves the library`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertLibraryFixture(A_SMALL)
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            assertEquals(LinkMangaSourcesResult.Linked, repository.link(A_SMALL, B))
            db.driver.execute(null, "UPDATE mangas SET favorite = 0 WHERE _id = $A_SMALL", 0)

            assertEquals(mapOf(1L to 1L, SOURCE_TWO to 2L, SOURCE_THREE to 1L), db.removableCounts())

            db.clearDatabase(listOf(SOURCE_TWO))

            assertEquals(listOf(A_SMALL, S, D), db.mangaIds())
            assertEquals(emptyList<MangaSourceLink>(), repository.getAll())
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

    @Test
    fun `cleanup of one source removes its unsaved manga and the link of an unsaved pair`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 5, url = "e", title = "E", source = 4, favorite = false)
            db.insertManga(id = 6, url = "f", title = "F", source = 5, favorite = false)
            val repository = MangaSourceLinkRepositoryImpl(db.handler)
            assertEquals(LinkMangaSourcesResult.Linked, repository.link(5, 6))

            db.clearDatabase(listOf(4L))

            assertEquals(listOf(6L), db.mangaIds())
            assertEquals(emptyList<MangaSourceLink>(), repository.getAll())
        }
    }

    private suspend fun MangaSourceLinkRepositoryImpl.linkSavedAndAlternate(
        saved: Long,
        savedFirst: Boolean,
    ): LinkMangaSourcesResult {
        return if (savedFirst) link(saved, B) else link(B, saved)
    }

    private fun InMemoryMangaDb.insertLibraryFixture(a: Long) {
        insertManga(id = a, url = "a", title = "A", source = 1)
        insertManga(id = B, url = "b", title = "B", source = SOURCE_TWO, favorite = false)
        insertManga(id = C, url = "c", title = "C", source = SOURCE_TWO, favorite = false)
        insertManga(id = S, url = "s", title = "S", source = SOURCE_TWO)
        insertManga(id = D, url = "d", title = "D", source = SOURCE_THREE, favorite = false)
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
            sql = "SELECT read, bookmark, last_page_read FROM chapters WHERE manga_id = $mangaId ORDER BY _id",
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
        const val A_SMALL = 1L
        const val A_LARGE = 10L
        const val B = 2L
        const val C = 3L
        const val S = 4L
        const val D = 5L
        const val SOURCE_TWO = 2L
        const val SOURCE_THREE = 3L
    }
}
