package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryAnimeDb
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryMangaDb
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category

class BackupRestorerCategoryLookupTest {

    private val backupCategories = listOf(BackupCategory(name = "Reading", order = 0))
    private val dbCategory = Category(id = 7, name = "Reading", order = 0, flags = 0, hidden = false)
    private val libraryOnly = RestoreOptions(
        appSettings = false,
        extensionRepoSettings = false,
        customButtons = false,
        sourceSettings = false,
        lightNovels = false,
    )

    @Test
    fun `manga restore reads the category list once for the whole library`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertCategory(id = 7, name = "Reading", order = 0)
            val getCategories = mockk<GetMangaCategories> {
                coEvery { await() } returns listOf(dbCategory)
            }
            val mangaRestorer = MangaRestorer(
                handler = db.handler,
                getCategories = getCategories,
                getMangaByUrlAndSourceId = mockk { coEvery { await(any(), any()) } returns null },
                getChaptersByMangaId = mockk { coEvery { await(any(), any()) } returns emptyList() },
                updateManga = mockk(relaxed = true),
                getTracks = mockk { coEvery { await(any()) } returns emptyList() },
                insertTrack = mockk(relaxed = true),
                fetchInterval = mockk(relaxed = true),
            )
            val backup = Backup(
                backupManga = List(3) {
                    BackupManga(source = 1, url = "/m/$it", title = "M$it", categories = listOf(0L))
                },
                backupCategories = backupCategories,
            )

            backupRestorer(mangaRestorer = mangaRestorer).restoreBackupData(backup, mockk(relaxed = true), libraryOnly)

            assertEquals(List(3) { 7L }, db.categoryLinks().map { it.second })
            coVerify(exactly = 1) { getCategories.await() }
        }
    }

    @Test
    fun `anime restore reads the category list once for the whole library`() = runTest {
        InMemoryAnimeDb().use { db ->
            db.insertCategory(id = 7, name = "Reading", order = 0)
            val getCategories = mockk<GetAnimeCategories> {
                coEvery { await() } returns listOf(dbCategory)
            }
            val animeRestorer = AnimeRestorer(
                handler = db.handler,
                getCategories = getCategories,
                getAnimeByUrlAndSourceId = mockk { coEvery { await(any(), any()) } returns null },
                getEpisodesByAnimeId = mockk { coEvery { await(any()) } returns emptyList() },
                updateAnime = mockk(relaxed = true),
                getTracks = mockk { coEvery { await(any()) } returns emptyList() },
                insertTrack = mockk(relaxed = true),
                fetchInterval = mockk(relaxed = true),
            )
            val backup = Backup(
                backupAnime = List(3) {
                    BackupAnime(source = 1, url = "/a/$it", title = "A$it", categories = listOf(0L))
                },
                backupAnimeCategories = backupCategories,
            )

            backupRestorer(animeRestorer = animeRestorer).restoreBackupData(backup, mockk(relaxed = true), libraryOnly)

            assertEquals(List(3) { 7L }, db.categoryLinks().map { it.second })
            coVerify(exactly = 1) { getCategories.await() }
        }
    }

    @Test
    fun `failed category lookup fails each manga entry and the restore continues`() = runTest {
        InMemoryMangaDb().use { db ->
            val mangaRestorer = MangaRestorer(
                handler = db.handler,
                getCategories = mockk { coEvery { await() } throws IllegalStateException("categories unreadable") },
                getMangaByUrlAndSourceId = mockk { coEvery { await(any(), any()) } returns null },
                getChaptersByMangaId = mockk { coEvery { await(any(), any()) } returns emptyList() },
                updateManga = mockk(relaxed = true),
                getTracks = mockk { coEvery { await(any()) } returns emptyList() },
                insertTrack = mockk(relaxed = true),
                fetchInterval = mockk(relaxed = true),
            )
            val backupAnime = BackupAnime(source = 1, url = "/a", title = "A")
            val animeRestorer = mockk<AnimeRestorer>(relaxed = true) {
                coEvery { sortByNew(any()) } returns listOf(backupAnime)
            }
            val backup = Backup(
                backupManga = List(2) { BackupManga(source = 1, url = "/m/$it", title = "M$it") },
                backupCategories = backupCategories,
                backupAnime = listOf(backupAnime),
            )

            backupRestorer(mangaRestorer = mangaRestorer, animeRestorer = animeRestorer)
                .restoreBackupData(backup, mockk(relaxed = true), libraryOnly)

            coVerify(exactly = 1) { animeRestorer.restore(any(), any()) }
        }
    }

    private fun backupRestorer(
        mangaRestorer: MangaRestorer = mockk(relaxed = true),
        animeRestorer: AnimeRestorer = mockk(relaxed = true),
    ) = BackupRestorer(
        context = mockk<Context>(relaxed = true),
        notifier = mockk(relaxed = true),
        isSync = false,
        animeCategoriesRestorer = mockk(relaxed = true),
        mangaCategoriesRestorer = mockk(relaxed = true),
        preferenceRestorer = mockk(relaxed = true),
        animeExtensionRepoRestorer = mockk(relaxed = true),
        mangaExtensionRepoRestorer = mockk(relaxed = true),
        customButtonRestorer = mockk(relaxed = true),
        animeRestorer = animeRestorer,
        mangaRestorer = mangaRestorer,
        extensionsRestorer = mockk(relaxed = true),
        lightNovelBackupDataSource = mockk(relaxed = true),
        animeStubSourceRepository = mockk(relaxed = true),
        mangaStubSourceRepository = mockk(relaxed = true),
        mangaDownloadCache = mockk(relaxed = true),
        animeDownloadCache = mockk(relaxed = true),
    )
}
