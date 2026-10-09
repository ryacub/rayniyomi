package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import app.cash.sqldelight.db.QueryResult
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeHistory
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeTracking
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupEpisode
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryAnimeDb
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.data.category.anime.AnimeCategoryRepositoryImpl
import tachiyomi.data.entries.anime.AnimeRepositoryImpl
import tachiyomi.data.items.episode.EpisodeRepositoryImpl
import tachiyomi.data.track.anime.AnimeTrackRepositoryImpl
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack

class BackupAnimeRestoreTest {

    @Test
    fun `idless backup restores each anime without parent links`() = runTest {
        InMemoryAnimeDb().use { db ->
            db.insertAnime(77, "unrelated", "Unrelated")
            db.insertAnime(10, "first", "First")
            db.driver.execute(null, "UPDATE animes SET parent_id = 77 WHERE _id = 10", 0)
            recordRestoreWrites(db)
            val entries = listOf(
                BackupAnime(source = 1, url = "first", title = "First"),
                BackupAnime(source = 1, url = "second", title = "Second"),
            )

            restore(db, entries)

            val rows = db.handler.awaitList { animesQueries.getAllAnime() }
            assertEquals(3, rows.size)
            entries.forEach { backup ->
                assertNull(rows.single { it.url == backup.url }.parent_id)
            }
            assertEquals(mapOf("first" to 1L, "second" to 1L), restoreWrites(db))
        }
    }

    @Test
    fun `season restore remaps its parent once and retains its details`() = runTest {
        listOf(true, false).forEach { parentFirst ->
            InMemoryAnimeDb().use { db ->
                db.insertAnime(900, "unrelated", "Unrelated")
                db.insertAnime(20, "parent", "Parent")
                db.insertAnime(30, "season", "Season")
                db.driver.execute(null, "UPDATE animes SET parent_id = 900 WHERE _id = 30", 0)
                db.handler.await { categoriesQueries.insert("Watching", 4, 0, null) }
                recordRestoreWrites(db)
                val parent = BackupAnime(
                    source = 1,
                    url = "parent",
                    title = "Parent",
                    id = 900,
                    lastModifiedAt = if (parentFirst) 2 else 0,
                )
                val season = BackupAnime(
                    source = 1, url = "season", title = "Season", id = 901, parentId = 900,
                    lastModifiedAt = 1,
                    episodes = listOf(BackupEpisode(url = "episode", name = "Episode", seen = true)),
                    history = listOf(BackupAnimeHistory(url = "episode", lastRead = 9_000)),
                    categories = listOf(4),
                    tracking = listOf(BackupAnimeTracking(syncId = 1, libraryId = 5, mediaId = 6)),
                )
                val orphan = BackupAnime(source = 1, url = "orphan", title = "Orphan", id = 902, parentId = 999)

                restore(db, listOf(parent, season, orphan), listOf(BackupCategory(name = "Watching", order = 4)))

                val rows = db.handler.awaitList { animesQueries.getAllAnime() }
                assertEquals(4, rows.size)
                val restoredSeason = rows.single { it.url == "season" }
                assertEquals(20L, restoredSeason.parent_id)
                assertNull(rows.single { it.url == "orphan" }.parent_id)
                assertNull(rows.single { it.url == "parent" }.parent_id)
                val episode = db.handler.awaitOne { episodesQueries.getEpisodeByUrlAndAnimeId("episode", 30) }
                assertEquals(true, episode.seen)
                assertEquals(listOf(Triple(1L, episode._id, 9_000L)), db.historyRows())
                val categories = AnimeCategoryRepositoryImpl(db.handler).getCategoriesByAnimeId(30)
                assertEquals("Watching", categories.single().name)
                val track = AnimeTrackRepositoryImpl(db.handler).getTracksByAnimeId(30).single()
                assertEquals(6L, track.remoteId)
                assertEquals(5L, track.libraryId)
                assertEquals(mapOf("parent" to 1L, "season" to 1L, "orphan" to 1L), restoreWrites(db))
            }
        }
    }

    @Test
    fun `failed parent restore leaves its season unattached and continues`() = runTest {
        InMemoryAnimeDb().use { db ->
            db.insertAnime(900, "unrelated", "Unrelated")
            db.insertAnime(30, "season", "Season")
            db.driver.execute(null, "UPDATE animes SET parent_id = 900 WHERE _id = 30", 0)
            db.driver.execute(
                null,
                "CREATE TRIGGER reject_parent BEFORE INSERT ON animes WHEN NEW.url = 'parent' BEGIN " +
                    "SELECT RAISE(ABORT, 'parent restore failed'); END",
                0,
            )
            val parent = BackupAnime(source = 1, url = "parent", title = "Parent", id = 900)
            val season = BackupAnime(
                source = 1,
                url = "season",
                title = "Season",
                id = 901,
                parentId = 900,
                episodes = listOf(BackupEpisode(url = "episode", name = "Episode", seen = true)),
            )
            val later = BackupAnime(source = 1, url = "later", title = "Later", id = 902)

            restore(db, listOf(parent, season, later))

            val rows = db.handler.awaitList { animesQueries.getAllAnime() }
            assertEquals(setOf("unrelated", "season", "later"), rows.map { it.url }.toSet())
            assertNull(rows.single { it.url == "season" }.parent_id)
            val episode = db.handler.awaitOne { episodesQueries.getEpisodeByUrlAndAnimeId("episode", 30) }
            assertEquals(true, episode.seen)
        }
    }

    private suspend fun restore(
        db: InMemoryAnimeDb,
        entries: List<BackupAnime>,
        categories: List<BackupCategory> = emptyList(),
    ) {
        val repository = AnimeRepositoryImpl(db.handler)
        val fetchInterval = mockk<AnimeFetchInterval> {
            every { getWindow(any()) } returns (0L to 0L)
        }
        val updateAnime = spyk(UpdateAnime(repository, fetchInterval))
        coEvery { updateAnime.awaitUpdateFetchInterval(any(), any(), any()) } returns true
        val trackRepository = AnimeTrackRepositoryImpl(db.handler)
        val animeRestorer = AnimeRestorer(
            handler = db.handler,
            getCategories = GetAnimeCategories(AnimeCategoryRepositoryImpl(db.handler)),
            getAnimeByUrlAndSourceId = GetAnimeByUrlAndSourceId(repository),
            getEpisodesByAnimeId = GetEpisodesByAnimeId(EpisodeRepositoryImpl(db.handler)),
            updateAnime = updateAnime,
            getTracks = GetAnimeTracks(trackRepository),
            insertTrack = InsertAnimeTrack(trackRepository),
            fetchInterval = fetchInterval,
        )
        val restorer = BackupRestorer(
            context = mockk<Context>(relaxed = true),
            notifier = mockk<BackupNotifier>(relaxed = true),
            isSync = false,
            animeCategoriesRestorer = mockk(relaxed = true),
            mangaCategoriesRestorer = mockk(relaxed = true),
            preferenceRestorer = mockk(relaxed = true),
            animeExtensionRepoRestorer = mockk(relaxed = true),
            mangaExtensionRepoRestorer = mockk(relaxed = true),
            customButtonRestorer = mockk(relaxed = true),
            animeRestorer = animeRestorer,
            mangaRestorer = mockk(relaxed = true),
            extensionsRestorer = mockk(relaxed = true),
            lightNovelBackupDataSource = mockk(relaxed = true),
            animeStubSourceRepository = mockk(relaxed = true),
            mangaStubSourceRepository = mockk(relaxed = true),
            mangaDownloadCache = mockk(relaxed = true),
            animeDownloadCache = mockk(relaxed = true),
        )
        withContext(Dispatchers.Default) {
            restorer.restoreBackupData(
                Backup(backupManga = emptyList(), backupAnime = entries, backupAnimeCategories = categories),
                mockk<Uri>(relaxed = true),
                RestoreOptions(
                    categories = categories.isNotEmpty(),
                    appSettings = false,
                    sourceSettings = false,
                    extensionRepoSettings = false,
                    customButtons = false,
                    lightNovels = false,
                ),
            )
        }
    }

    private fun recordRestoreWrites(db: InMemoryAnimeDb) {
        db.driver.execute(null, "CREATE TABLE restore_writes(url TEXT NOT NULL)", 0)
        db.driver.execute(
            null,
            "CREATE TRIGGER restore_insert AFTER INSERT ON animes BEGIN " +
                "INSERT INTO restore_writes VALUES(NEW.url); END",
            0,
        )
        db.driver.execute(
            null,
            "CREATE TRIGGER restore_update AFTER UPDATE OF title ON animes WHEN NEW.is_syncing = 1 BEGIN " +
                "INSERT INTO restore_writes VALUES(NEW.url); END",
            0,
        )
    }

    private fun restoreWrites(db: InMemoryAnimeDb): Map<String, Long> {
        val writes = mutableMapOf<String, Long>()
        db.driver.executeQuery(
            null,
            "SELECT url, COUNT(*) FROM restore_writes GROUP BY url",
            { cursor ->
                while (cursor.next().value) {
                    writes[requireNotNull(cursor.getString(0))] = requireNotNull(cursor.getLong(1))
                }
                QueryResult.Unit
            },
            0,
        )
        return writes
    }
}
