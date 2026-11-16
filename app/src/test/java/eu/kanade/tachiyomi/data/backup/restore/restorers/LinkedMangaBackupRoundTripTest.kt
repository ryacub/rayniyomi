package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import android.net.Uri
import eu.kanade.domain.entries.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.create.BackupCreator
import eu.kanade.tachiyomi.data.backup.create.BackupFileWriter
import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeCategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeExtensionRepoBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.AnimeSourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.CustomButtonBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.ExtensionsBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.LightNovelBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaCategoriesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaExtensionRepoBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.MangaSourcesBackupCreator
import eu.kanade.tachiyomi.data.backup.create.creators.PreferenceBackupCreator
import eu.kanade.tachiyomi.data.backup.lightnovel.LightNovelBackupDataSource
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.LegacyBackup
import eu.kanade.tachiyomi.data.backup.restore.BackupRestorer
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.CustomButtonRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.entries.manga.MangaRepositoryImpl
import tachiyomi.data.entries.manga.MangaSourceLinkRepositoryImpl
import tachiyomi.data.items.chapter.ChapterRepositoryImpl
import tachiyomi.domain.backup.service.BackupPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnimeFavorites
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.GetMangaFavorites
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.track.manga.interactor.GetMangaTracks
import tachiyomi.domain.track.manga.interactor.InsertMangaTrack

class LinkedMangaBackupRoundTripTest {

    @Test
    fun `linked unread nonfavorite is backed up restored with new ids and replayed once`() = runTest {
        val options = BackupOptions(
            libraryEntries = true,
            categories = false,
            chapters = true,
            tracking = false,
            history = false,
            readEntries = false,
            appSettings = false,
            extensionRepoSettings = false,
            customButton = false,
            sourceSettings = false,
            extensions = false,
            lightNovels = false,
        )
        val backup = InMemoryMangaDb().use { source ->
            source.insertManga(id = 101L, url = "first", title = "First", source = 11L)
            source.insertManga(id = 102L, url = "second", title = "Unread second", source = 12L)
            source.insertChapter(id = 201L, mangaId = 101L, url = "first-chapter")
            source.driver.execute(
                null,
                "UPDATE chapters SET last_page_read = 7, bookmark = 1 WHERE _id = 201",
                0,
            )
            source.driver.execute(null, "UPDATE mangas SET favorite = 0 WHERE _id = 102", 0)
            val mangaRepository = MangaRepositoryImpl(source.handler)
            val sourceLinkRepository = MangaSourceLinkRepositoryImpl(source.handler)
            sourceLinkRepository.link(101L, 102L)
            val mangaBackupCreator = MangaBackupCreator(
                handler = source.handler,
                getCategories = mockk(relaxed = true),
                getHistory = mockk(relaxed = true),
            )
            val creator = createBackupCreator(
                context = mockk(relaxed = true),
                getMangaFavorites = GetMangaFavorites(mangaRepository),
                mangaRepository = mangaRepository,
                mangaSourceLinkRepository = sourceLinkRepository,
                mangaBackupCreator = mangaBackupCreator,
            )

            val mangaBackupData = creator.createMangaSourceBackupData(options)
            assertEquals(
                listOf("first", "second"),
                mangaBackupData.backupManga.map { it.url }.sorted(),
            )
            assertEquals(false, mangaBackupData.backupManga.single { it.url == "second" }.favorite)
            assertEquals(1, mangaBackupData.backupMangaSourceLinks.size)

            ProtoBuf.decodeFromByteArray(
                Backup.serializer(),
                ProtoBuf.encodeToByteArray(
                    Backup.serializer(),
                    Backup(
                        backupManga = mangaBackupData.backupManga,
                        backupMangaSourceLinks = mangaBackupData.backupMangaSourceLinks,
                    ),
                ),
            )
        }

        InMemoryMangaDb().use { target ->
            val mangaRepository = MangaRepositoryImpl(target.handler)
            val chapterRepository = ChapterRepositoryImpl(target.handler)
            val sourceLinkRepository = MangaSourceLinkRepositoryImpl(target.handler)
            val getChapters = GetChaptersByMangaId(chapterRepository)
            val mangaRestorer = MangaRestorer(
                handler = target.handler,
                getCategories = mockk(relaxed = true),
                getMangaByUrlAndSourceId = GetMangaByUrlAndSourceId(mangaRepository),
                getChaptersByMangaId = getChapters,
                updateManga = mockk<UpdateManga>(relaxed = true) {
                    coEvery { awaitUpdateFetchInterval(any(), any(), any()) } returns true
                },
                getTracks = mockk<GetMangaTracks>(relaxed = true) {
                    coEvery { await(any()) } returns emptyList()
                },
                insertTrack = mockk<InsertMangaTrack>(relaxed = true),
                fetchInterval = MangaFetchInterval(getChapters),
            )
            val restorer = createBackupRestorer(mangaRestorer, sourceLinkRepository)

            restorer.restoreBackupData(backup, mockk<Uri>(relaxed = true), libraryOnlyOptions())
            val restoredLinks = sourceLinkRepository.getAllWithManga()
            assertEquals(1, restoredLinks.size)
            val restoredLink = restoredLinks.single()
            assertNotEquals(101L, restoredLink.firstManga.id)
            assertNotEquals(102L, restoredLink.secondManga.id)
            assertEquals("first", restoredLink.firstManga.url)
            assertEquals("second", restoredLink.secondManga.url)
            assertEquals(false, restoredLink.secondManga.favorite)
            val restoredFirstChapter = chapterRepository.getChapterByMangaId(restoredLink.firstManga.id).single()
            assertEquals(7L, restoredFirstChapter.lastPageRead)
            assertEquals(true, restoredFirstChapter.bookmark)

            restorer.restoreBackupData(backup, mockk<Uri>(relaxed = true), libraryOnlyOptions())

            assertEquals(1, sourceLinkRepository.getAll().size)
            assertEquals("first", mangaRepository.getMangaByUrlAndSourceId("first", 11L)?.url)
            assertEquals("second", mangaRepository.getMangaByUrlAndSourceId("second", 12L)?.url)
        }

        val legacyBytes = ProtoBuf.encodeToByteArray(
            LegacyBackup.serializer(),
            LegacyBackup(backupManga = backup.backupManga),
        )
        val legacy = ProtoBuf.decodeFromByteArray(Backup.serializer(), legacyBytes)
        assertTrue(legacy.backupMangaSourceLinks.isEmpty())
    }

    private fun createBackupCreator(
        context: Context,
        getMangaFavorites: GetMangaFavorites,
        mangaRepository: tachiyomi.domain.entries.manga.repository.MangaRepository,
        mangaSourceLinkRepository: tachiyomi.domain.entries.manga.repository.MangaSourceLinkRepository,
        mangaBackupCreator: MangaBackupCreator,
    ) = BackupCreator(
        context = context,
        isAutoBackup = false,
        parser = ProtoBuf,
        getAnimeFavorites = mockk<GetAnimeFavorites>(relaxed = true),
        getMangaFavorites = getMangaFavorites,
        backupPreferences = mockk<BackupPreferences>(relaxed = true),
        mangaRepository = mangaRepository,
        mangaSourceLinkRepository = mangaSourceLinkRepository,
        animeRepository = mockk<AnimeRepository>(relaxed = true),
        animeCategoriesBackupCreator = mockk<AnimeCategoriesBackupCreator>(relaxed = true),
        mangaCategoriesBackupCreator = mockk<MangaCategoriesBackupCreator>(relaxed = true),
        animeBackupCreator = mockk<AnimeBackupCreator>(relaxed = true),
        mangaBackupCreator = mangaBackupCreator,
        preferenceBackupCreator = mockk<PreferenceBackupCreator>(relaxed = true),
        animeExtensionRepoBackupCreator = mockk<AnimeExtensionRepoBackupCreator>(relaxed = true),
        mangaExtensionRepoBackupCreator = mockk<MangaExtensionRepoBackupCreator>(relaxed = true),
        customButtonBackupCreator = mockk<CustomButtonBackupCreator>(relaxed = true),
        animeSourcesBackupCreator = mockk<AnimeSourcesBackupCreator>(relaxed = true),
        mangaSourcesBackupCreator = mockk<MangaSourcesBackupCreator>(relaxed = true),
        extensionsBackupCreator = mockk<ExtensionsBackupCreator>(relaxed = true),
        lightNovelBackupCreator = mockk<LightNovelBackupCreator>(relaxed = true),
        backupFileWriter = mockk<BackupFileWriter>(relaxed = true),
    )

    private fun createBackupRestorer(
        mangaRestorer: MangaRestorer,
        mangaSourceLinkRepository: MangaSourceLinkRepositoryImpl,
    ) = BackupRestorer(
        context = mockk<Context>(relaxed = true),
        notifier = mockk<BackupNotifier>(relaxed = true),
        isSync = false,
        animeCategoriesRestorer = mockk<AnimeCategoriesRestorer>(relaxed = true),
        mangaCategoriesRestorer = mockk<MangaCategoriesRestorer>(relaxed = true),
        preferenceRestorer = mockk<PreferenceRestorer>(relaxed = true),
        animeExtensionRepoRestorer = mockk<AnimeExtensionRepoRestorer>(relaxed = true),
        mangaExtensionRepoRestorer = mockk<MangaExtensionRepoRestorer>(relaxed = true),
        customButtonRestorer = mockk<CustomButtonRestorer>(relaxed = true),
        animeRestorer = mockk<AnimeRestorer>(relaxed = true),
        mangaRestorer = mangaRestorer,
        extensionsRestorer = mockk<ExtensionsRestorer>(relaxed = true),
        lightNovelBackupDataSource = mockk<LightNovelBackupDataSource>(relaxed = true),
        animeStubSourceRepository = mockk(relaxed = true),
        mangaStubSourceRepository = mockk(relaxed = true),
        mangaDownloadCache = mockk<MangaDownloadCache>(relaxed = true),
        animeDownloadCache = mockk<AnimeDownloadCache>(relaxed = true),
        mangaSourceLinkRepository = mangaSourceLinkRepository,
    )

    private fun libraryOnlyOptions() = RestoreOptions(
        libraryEntries = true,
        categories = false,
        appSettings = false,
        extensionRepoSettings = false,
        customButtons = false,
        sourceSettings = false,
        extensions = false,
        lightNovels = false,
    )
}
