package eu.kanade.tachiyomi.data.backup.create.creators

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryAnimeDb
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryMangaDb
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.history.anime.AnimeHistoryRepositoryImpl
import tachiyomi.data.history.manga.MangaHistoryRepositoryImpl
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.history.anime.interactor.GetAnimeHistory
import tachiyomi.domain.history.manga.interactor.GetMangaHistory

class BackupCreatorHistoryQueryTest {

    private val historyOnly = BackupOptions(
        categories = false,
        chapters = false,
        tracking = false,
        history = true,
    )

    @Test
    fun `manga backup reads history with a query count that does not grow with history rows`() = runTest {
        val (oneRowQueries, oneRowUrls) = backUpManga(historyRows = 1)
        val (manyRowQueries, manyRowUrls) = backUpManga(historyRows = 5)

        assertEquals(listOf("ch-0"), oneRowUrls)
        assertEquals((0 until 5).map { "ch-$it" }, manyRowUrls)
        assertEquals(oneRowQueries, manyRowQueries)
    }

    @Test
    fun `anime backup reads history with a query count that does not grow with history rows`() = runTest {
        val (oneRowQueries, oneRowUrls) = backUpAnime(historyRows = 1)
        val (manyRowQueries, manyRowUrls) = backUpAnime(historyRows = 5)

        assertEquals(listOf("ep-0"), oneRowUrls)
        assertEquals((0 until 5).map { "ep-$it" }, manyRowUrls)
        assertEquals(oneRowQueries, manyRowQueries)
    }

    private suspend fun backUpManga(historyRows: Int): Pair<Int, List<String>> = InMemoryMangaDb().use { db ->
        db.insertManga(id = 1, url = "manga", title = "Manga")
        repeat(historyRows) {
            db.insertChapter(id = 10L + it, mangaId = 1, url = "ch-$it")
            db.insertHistory(id = 100L + it, chapterId = 10L + it, lastRead = 1_000)
        }
        val creator = MangaBackupCreator(
            handler = db.handler,
            getCategories = mockk(relaxed = true),
            getHistory = GetMangaHistory(MangaHistoryRepositoryImpl(db.handler)),
        )
        db.driver.resetQueryCount()

        val backup = creator(listOf(Manga.create().copy(id = 1)), historyOnly).single()

        db.driver.queryCount to backup.history.map { it.url }.sorted()
    }

    private suspend fun backUpAnime(historyRows: Int): Pair<Int, List<String>> = InMemoryAnimeDb().use { db ->
        db.insertAnime(id = 1, url = "anime", title = "Anime")
        repeat(historyRows) {
            db.insertEpisode(id = 10L + it, animeId = 1, url = "ep-$it")
            db.insertHistory(id = 100L + it, episodeId = 10L + it, lastSeen = 1_000)
        }
        val creator = AnimeBackupCreator(
            handler = db.handler,
            getCategories = mockk(relaxed = true),
            getHistory = GetAnimeHistory(AnimeHistoryRepositoryImpl(db.handler)),
        )
        db.driver.resetQueryCount()

        val backup = creator(listOf(Anime.create().copy(id = 1)), historyOnly).single()

        db.driver.queryCount to backup.history.map { it.url }.sorted()
    }
}
