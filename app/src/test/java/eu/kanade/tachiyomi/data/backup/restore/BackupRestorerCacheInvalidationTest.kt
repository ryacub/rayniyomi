package eu.kanade.tachiyomi.data.backup.restore

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.lightnovel.LightNovelBackupDataSource
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.CustomButtonRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.io.File
import java.io.FileInputStream

class BackupRestorerCacheInvalidationTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun registerProtoBuf() {
            Injekt.addSingleton<ProtoBuf>(ProtoBuf)
        }
    }

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `completed restore with library entries invalidates manga and anime download caches`() = runTest {
        val backup = Backup(
            backupManga = listOf(BackupManga(source = 1, url = "m1", title = "M1")),
            backupAnime = listOf(BackupAnime(source = 1, url = "a1", title = "A1")),
        )
        val file = tempBackup(backup)
        val notifier = mockk<BackupNotifier>(relaxed = true)
        val mangaDownloadCache = mockk<MangaDownloadCache>(relaxed = true)
        val animeDownloadCache = mockk<AnimeDownloadCache>(relaxed = true)
        val mangaRestorer = mockk<MangaRestorer>(relaxed = true)
        val animeRestorer = mockk<AnimeRestorer>(relaxed = true)
        val restorer = createRestorer(
            file = file,
            notifier = notifier,
            mangaRestorer = mangaRestorer,
            animeRestorer = animeRestorer,
            mangaDownloadCache = mangaDownloadCache,
            animeDownloadCache = animeDownloadCache,
        )

        coEvery { mangaRestorer.sortByNew(any()) } returns backup.backupManga
        coEvery { animeRestorer.sortByNew(any()) } returns backup.backupAnime

        restorer.restore(mockk<Uri>(), options(libraryEntries = true))

        verify(exactly = 1) { mangaDownloadCache.invalidateCache() }
        verify(exactly = 1) { animeDownloadCache.invalidateCache() }
        verifyOrder {
            mangaDownloadCache.invalidateCache()
            animeDownloadCache.invalidateCache()
            notifier.showRestoreComplete(any(), 0, any(), false)
        }
    }

    @Test
    fun `restore with recorded entry failures still invalidates both caches`() = runTest {
        val backup = Backup(backupManga = listOf(BackupManga(source = 1, url = "m", title = "M")))
        val file = tempBackup(backup)
        val context = contextFor(file).apply {
            every { externalCacheDir } returns tempDir
        }
        val notifier = mockk<BackupNotifier>(relaxed = true)
        val mangaDownloadCache = mockk<MangaDownloadCache>(relaxed = true)
        val animeDownloadCache = mockk<AnimeDownloadCache>(relaxed = true)
        val mangaRestorer = mockk<MangaRestorer>(relaxed = true)
        val animeRestorer = mockk<AnimeRestorer>(relaxed = true)
        val restorer = createRestorer(
            context = context,
            notifier = notifier,
            mangaRestorer = mangaRestorer,
            animeRestorer = animeRestorer,
            mangaDownloadCache = mangaDownloadCache,
            animeDownloadCache = animeDownloadCache,
        )

        coEvery { mangaRestorer.sortByNew(any()) } returns backup.backupManga
        coEvery { animeRestorer.sortByNew(any()) } returns backup.backupAnime
        coEvery { mangaRestorer.restore(any(), any()) } throws RuntimeException("boom")

        restorer.restore(mockk<Uri>(), options(libraryEntries = true))

        verify(exactly = 1) { mangaDownloadCache.invalidateCache() }
        verify(exactly = 1) { animeDownloadCache.invalidateCache() }
        verify { notifier.showRestoreComplete(any(), 1, any(), false) }
    }

    @Test
    fun `restore without library entries selected does not invalidate caches`() = runTest {
        val backup = Backup(backupManga = listOf(BackupManga(source = 1, url = "m", title = "M")))
        val file = tempBackup(backup)
        val notifier = mockk<BackupNotifier>(relaxed = true)
        val mangaDownloadCache = mockk<MangaDownloadCache>(relaxed = true)
        val animeDownloadCache = mockk<AnimeDownloadCache>(relaxed = true)
        val mangaRestorer = mockk<MangaRestorer>(relaxed = true)
        val animeRestorer = mockk<AnimeRestorer>(relaxed = true)
        val restorer = createRestorer(
            file = file,
            notifier = notifier,
            mangaRestorer = mangaRestorer,
            animeRestorer = animeRestorer,
            mangaDownloadCache = mangaDownloadCache,
            animeDownloadCache = animeDownloadCache,
        )

        coEvery { mangaRestorer.sortByNew(any()) } returns backup.backupManga
        coEvery { animeRestorer.sortByNew(any()) } returns backup.backupAnime

        restorer.restore(mockk<Uri>(), options(libraryEntries = false))

        verify(exactly = 0) { mangaDownloadCache.invalidateCache() }
        verify(exactly = 0) { animeDownloadCache.invalidateCache() }
        verify { notifier.showRestoreComplete(any(), 0, any(), false) }
    }

    @Test
    fun `cancelled restore does not invalidate caches`() = runTest {
        val backup = Backup(backupAnime = listOf(BackupAnime(source = 1, url = "a1", title = "A1")))
        val file = tempBackup(backup)
        val notifier = mockk<BackupNotifier>(relaxed = true)
        val mangaDownloadCache = mockk<MangaDownloadCache>(relaxed = true)
        val animeDownloadCache = mockk<AnimeDownloadCache>(relaxed = true)
        val mangaRestorer = mockk<MangaRestorer>(relaxed = true)
        val animeRestorer = mockk<AnimeRestorer>(relaxed = true)
        val restorer = createRestorer(
            file = file,
            notifier = notifier,
            mangaRestorer = mangaRestorer,
            animeRestorer = animeRestorer,
            mangaDownloadCache = mangaDownloadCache,
            animeDownloadCache = animeDownloadCache,
        )

        val started = Channel<Unit>(capacity = 1)
        val gate = CompletableDeferred<Unit>()
        coEvery { mangaRestorer.sortByNew(any()) } returns backup.backupManga
        coEvery { animeRestorer.sortByNew(any()) } returns backup.backupAnime
        coEvery { animeRestorer.restore(any(), any(), any()) } coAnswers {
            started.send(Unit)
            gate.await()
        }

        val job = launch(Dispatchers.Default) {
            restorer.restore(mockk<Uri>(), options(libraryEntries = true))
        }
        started.receive()
        job.cancel()
        gate.complete(Unit)
        job.join()

        assertTrue(job.isCancelled)
        verify(exactly = 0) { mangaDownloadCache.invalidateCache() }
        verify(exactly = 0) { animeDownloadCache.invalidateCache() }
        verify(exactly = 0) { notifier.showRestoreComplete(any(), any(), any(), any()) }
    }

    @Test
    fun `cache invalidation failure does not replace the restore result`() = runTest {
        val backup = Backup(backupManga = listOf(BackupManga(source = 1, url = "m", title = "M")))
        val file = tempBackup(backup)
        val notifier = mockk<BackupNotifier>(relaxed = true)
        val mangaDownloadCache = mockk<MangaDownloadCache>(relaxed = true)
        val animeDownloadCache = mockk<AnimeDownloadCache>(relaxed = true)
        val mangaRestorer = mockk<MangaRestorer>(relaxed = true)
        val animeRestorer = mockk<AnimeRestorer>(relaxed = true)
        val restorer = createRestorer(
            file = file,
            notifier = notifier,
            mangaRestorer = mangaRestorer,
            animeRestorer = animeRestorer,
            mangaDownloadCache = mangaDownloadCache,
            animeDownloadCache = animeDownloadCache,
        )

        coEvery { mangaRestorer.sortByNew(any()) } returns backup.backupManga
        coEvery { animeRestorer.sortByNew(any()) } returns backup.backupAnime
        every { mangaDownloadCache.invalidateCache() } throws RuntimeException("cache blew up")
        every { animeDownloadCache.invalidateCache() } throws RuntimeException("cache blew up")

        restorer.restore(mockk<Uri>(), options(libraryEntries = true))

        verify(exactly = 1) { mangaDownloadCache.invalidateCache() }
        verify(exactly = 1) { animeDownloadCache.invalidateCache() }
        verify { notifier.showRestoreComplete(any(), 0, any(), false) }
    }

    @Test
    fun `restore with library entries selected but no entries restored still invalidates caches`() = runTest {
        val backup = Backup(backupManga = listOf(BackupManga(source = 1, url = "m", title = "M")))
        val file = tempBackup(backup)
        val notifier = mockk<BackupNotifier>(relaxed = true)
        val mangaDownloadCache = mockk<MangaDownloadCache>(relaxed = true)
        val animeDownloadCache = mockk<AnimeDownloadCache>(relaxed = true)
        val mangaRestorer = mockk<MangaRestorer>(relaxed = true)
        val animeRestorer = mockk<AnimeRestorer>(relaxed = true)
        val restorer = createRestorer(
            file = file,
            notifier = notifier,
            mangaRestorer = mangaRestorer,
            animeRestorer = animeRestorer,
            mangaDownloadCache = mangaDownloadCache,
            animeDownloadCache = animeDownloadCache,
        )

        coEvery { mangaRestorer.sortByNew(any()) } returns emptyList()
        coEvery { animeRestorer.sortByNew(any()) } returns emptyList()

        restorer.restore(mockk<Uri>(), options(libraryEntries = true))

        verify(exactly = 1) { mangaDownloadCache.invalidateCache() }
        verify(exactly = 1) { animeDownloadCache.invalidateCache() }
    }

    private fun tempBackup(backup: Backup): File {
        val file = File(tempDir, "backup.proto")
        file.writeBytes(ProtoBuf.encodeToByteArray(Backup.serializer(), backup))
        return file
    }

    private fun contextFor(file: File): Context {
        val context = mockk<Context>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        every { contentResolver.openInputStream(any()) } answers { FileInputStream(file) }
        return context
    }

    private fun createRestorer(
        file: File = File(tempDir, "missing.proto"),
        context: Context = contextFor(file),
        notifier: BackupNotifier = mockk(relaxed = true),
        mangaRestorer: MangaRestorer = mockk(relaxed = true),
        animeRestorer: AnimeRestorer = mockk(relaxed = true),
        mangaDownloadCache: MangaDownloadCache = mockk(relaxed = true),
        animeDownloadCache: AnimeDownloadCache = mockk(relaxed = true),
    ): BackupRestorer {
        return BackupRestorer(
            context = context,
            notifier = notifier,
            isSync = false,
            animeCategoriesRestorer = mockk<AnimeCategoriesRestorer>(relaxed = true),
            mangaCategoriesRestorer = mockk<MangaCategoriesRestorer>(relaxed = true),
            preferenceRestorer = mockk<PreferenceRestorer>(relaxed = true),
            animeExtensionRepoRestorer = mockk<AnimeExtensionRepoRestorer>(relaxed = true),
            mangaExtensionRepoRestorer = mockk<MangaExtensionRepoRestorer>(relaxed = true),
            customButtonRestorer = mockk<CustomButtonRestorer>(relaxed = true),
            animeRestorer = animeRestorer,
            mangaRestorer = mangaRestorer,
            extensionsRestorer = mockk<ExtensionsRestorer>(relaxed = true),
            lightNovelBackupDataSource = mockk<LightNovelBackupDataSource>(relaxed = true),
            animeStubSourceRepository = mockk<AnimeStubSourceRepository>(relaxed = true),
            mangaStubSourceRepository = mockk<MangaStubSourceRepository>(relaxed = true),
            mangaDownloadCache = mangaDownloadCache,
            animeDownloadCache = animeDownloadCache,
        )
    }

    private fun options(libraryEntries: Boolean) = RestoreOptions(
        libraryEntries = libraryEntries,
        categories = false,
        appSettings = false,
        extensionRepoSettings = false,
        customButtons = false,
        sourceSettings = false,
        extensions = false,
        lightNovels = false,
    )
}
