package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.lightnovel.LightNovelBackupDataSource
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.source.anime.repository.AnimeStubSourceRepository
import tachiyomi.domain.source.manga.repository.MangaStubSourceRepository
import java.lang.reflect.Method
import java.util.Collections
import java.util.Date
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BackupRestorerTest {

    @Test
    fun `concurrent progress updates do not lose increments`() = runTest {
        val total = 64
        val notifier = mockk<BackupNotifier>(relaxed = true)
        val restorer = createRestorer(notifier = notifier)
        restorer.setRestoreAmount(total)

        val gate = CompletableDeferred<Unit>()

        coroutineScope {
            val tasks = List(total) {
                async(Dispatchers.Default) {
                    gate.await()
                    restorer.invokeIncrementProgressAndNotify("item-$it")
                }
            }
            gate.complete(Unit)
            tasks.forEach { it.await() }
        }

        assertEquals(total, restorer.getRestoreProgressValue())
        verify(exactly = total) { notifier.showRestoreProgress(any(), any(), total, false) }
    }

    @Test
    fun `concurrent error recording keeps all entries`() = runTest {
        val totalErrors = 40
        val restorer = createRestorer()
        val gate = CompletableDeferred<Unit>()

        coroutineScope {
            val jobs = List(totalErrors) { idx ->
                launch(Dispatchers.Default) {
                    gate.await()
                    restorer.invokeRecordError("err-$idx")
                }
            }
            gate.complete(Unit)
            jobs.joinAll()
        }

        assertEquals(totalErrors, restorer.getErrorCount())
    }

    @Test
    fun `cancellation does not deadlock and does not over increment`() = runTest {
        val notifier = mockk<BackupNotifier>(relaxed = true)
        val restorer = createRestorer(notifier = notifier)
        restorer.setRestoreAmount(2)

        val gate = CompletableDeferred<Unit>()
        val started = Channel<Unit>(capacity = 2)

        val job1 = launch(Dispatchers.Default) {
            started.send(Unit)
            gate.await()
            ensureActive()
            restorer.invokeIncrementProgressAndNotify("first")
        }
        val job2 = launch(Dispatchers.Default) {
            started.send(Unit)
            gate.await()
            ensureActive()
            restorer.invokeIncrementProgressAndNotify("second")
        }

        repeat(2) { started.receive() }
        job2.cancel()
        gate.complete(Unit)

        joinAll(job1, job2)

        assertEquals(1, restorer.getRestoreProgressValue())
        verify(exactly = 1) { notifier.showRestoreProgress(any(), any(), 2, false) }
    }

    @Test
    fun `low total progress path reports expected bounds`() = runTest {
        val notifier = mockk<BackupNotifier>(relaxed = true)
        val restorer = createRestorer(notifier = notifier)
        restorer.setRestoreAmount(1)

        val progress = restorer.invokeIncrementProgressAndNotify("single")

        assertEquals(1, progress)
        assertEquals(1, restorer.getRestoreProgressValue())
        verify(exactly = 1) { notifier.showRestoreProgress("single", 1, 1, false) }
    }

    @Test
    fun `plugin rejection records a light novel restore error while success does not`() = runTest {
        val metadata = byteArrayOf(1, 2, 3)
        val rejectedDataSource = mockk<LightNovelBackupDataSource>(relaxed = true)
        every { rejectedDataSource.isPluginInstalled() } returns true
        coEvery { rejectedDataSource.restoreBackup(metadata) } returns false
        val rejectedRestorer = createRestorer(lightNovelBackupDataSource = rejectedDataSource)

        assertEquals(LightNovelRestoreOutcome.PluginRejected, rejectedRestorer.invokeRestoreLightNovels(metadata))

        assertEquals(1, rejectedRestorer.getErrorCount())
        assertTrue(rejectedRestorer.getErrorMessages().single().contains("retry", ignoreCase = true))
        coVerify(exactly = 1) { rejectedDataSource.restoreBackup(metadata) }

        val successfulDataSource = mockk<LightNovelBackupDataSource>(relaxed = true)
        every { successfulDataSource.isPluginInstalled() } returns true
        coEvery { successfulDataSource.restoreBackup(metadata) } returns true
        val successfulRestorer = createRestorer(lightNovelBackupDataSource = successfulDataSource)

        assertEquals(LightNovelRestoreOutcome.Restored, successfulRestorer.invokeRestoreLightNovels(metadata))

        assertEquals(0, successfulRestorer.getErrorCount())
        coVerify(exactly = 1) { successfulDataSource.restoreBackup(metadata) }
    }

    @Test
    fun `missing plugin reports an error only when light novel metadata exists`() = runTest {
        val metadata = byteArrayOf(4, 5, 6)
        val dataSource = mockk<LightNovelBackupDataSource>(relaxed = true)
        every { dataSource.isPluginInstalled() } returns false
        val restorer = createRestorer(lightNovelBackupDataSource = dataSource)

        assertEquals(LightNovelRestoreOutcome.NoMetadata, restorer.invokeRestoreLightNovels(null))

        assertEquals(0, restorer.getErrorCount())
        verify(exactly = 0) { dataSource.isPluginInstalled() }

        assertEquals(LightNovelRestoreOutcome.PluginRequired, restorer.invokeRestoreLightNovels(metadata))

        assertEquals(1, restorer.getErrorCount())
        assertTrue(restorer.getErrorMessages().single().contains("install", ignoreCase = true))
        coVerify(exactly = 0) { dataSource.restoreBackup(any()) }
    }

    @Test
    fun `categories finish before library entries and app settings start`() = runTest {
        val events = Collections.synchronizedList(mutableListOf<String>())
        val mangaCategoriesRestorer = mockk<MangaCategoriesRestorer>(relaxed = true)
        coEvery { mangaCategoriesRestorer(any()) } coAnswers {
            delay(50)
            events.add("manga-categories-done")
        }
        val mangaRestorer = mockk<MangaRestorer>(relaxed = true)
        val backupManga = BackupManga(source = 1L, url = "/m", title = "m")
        coEvery { mangaRestorer.sortByNew(any()) } returns listOf(backupManga)
        coEvery { mangaRestorer.restore(any(), any()) } coAnswers { events.add("manga-restore") }
        val preferenceRestorer = mockk<PreferenceRestorer>(relaxed = true)
        coEvery { preferenceRestorer.restoreApp(any(), any(), any()) } coAnswers { events.add("app-prefs") }
        val restorer = createRestorer(
            mangaCategoriesRestorer = mangaCategoriesRestorer,
            mangaRestorer = mangaRestorer,
            preferenceRestorer = preferenceRestorer,
        )
        val backup = Backup(
            backupManga = listOf(backupManga),
            backupCategories = listOf(BackupCategory(name = "Reading", order = 0)),
        )

        withContext(Dispatchers.Default) {
            restorer.restoreBackupData(
                backup,
                mockk<Uri>(relaxed = true),
                RestoreOptions(
                    sourceSettings = false,
                    extensionRepoSettings = false,
                    customButtons = false,
                    lightNovels = false,
                ),
            )
        }

        assertEquals("manga-categories-done", events.first())
        assertTrue(events.containsAll(listOf("manga-restore", "app-prefs")))
    }

    private fun createRestorer(
        notifier: BackupNotifier = mockk(relaxed = true),
        lightNovelBackupDataSource: LightNovelBackupDataSource = mockk(relaxed = true),
        mangaCategoriesRestorer: MangaCategoriesRestorer = mockk(relaxed = true),
        mangaRestorer: MangaRestorer = mockk(relaxed = true),
        preferenceRestorer: PreferenceRestorer = mockk(relaxed = true),
    ): BackupRestorer {
        return BackupRestorer(
            context = mockk<Context>(relaxed = true),
            notifier = notifier,
            isSync = false,
            animeCategoriesRestorer = mockk<AnimeCategoriesRestorer>(relaxed = true),
            mangaCategoriesRestorer = mangaCategoriesRestorer,
            preferenceRestorer = preferenceRestorer,
            animeExtensionRepoRestorer = mockk<AnimeExtensionRepoRestorer>(relaxed = true),
            mangaExtensionRepoRestorer = mockk<MangaExtensionRepoRestorer>(relaxed = true),
            customButtonRestorer = mockk<CustomButtonRestorer>(relaxed = true),
            animeRestorer = mockk<AnimeRestorer>(relaxed = true),
            mangaRestorer = mangaRestorer,
            extensionsRestorer = mockk<ExtensionsRestorer>(relaxed = true),
            lightNovelBackupDataSource = lightNovelBackupDataSource,
            animeStubSourceRepository = mockk<AnimeStubSourceRepository>(relaxed = true),
            mangaStubSourceRepository = mockk<MangaStubSourceRepository>(relaxed = true),
            mangaDownloadCache = mockk<MangaDownloadCache>(relaxed = true),
            animeDownloadCache = mockk<AnimeDownloadCache>(relaxed = true),
        )
    }

    private fun BackupRestorer.setRestoreAmount(value: Int) {
        val field = BackupRestorer::class.java.getDeclaredField("restoreAmount")
        field.isAccessible = true
        field.setInt(this, value)
    }

    private fun BackupRestorer.invokeIncrementProgressAndNotify(content: String): Int {
        val method = BackupRestorer::class.java.getDeclaredMethod("incrementProgressAndNotify", String::class.java)
        method.isAccessible = true
        return method.invoke(this, content) as Int
    }

    private fun BackupRestorer.invokeRecordError(message: String) {
        val method = BackupRestorer::class.java.getDeclaredMethod("recordError", String::class.java)
        method.isAccessible = true
        method.invoke(this, message)
    }

    private suspend fun BackupRestorer.invokeRestoreLightNovels(data: ByteArray?): LightNovelRestoreOutcome =
        suspendCoroutine { continuation ->
            val method = restoreLightNovelsMethod()
            val result = method.invoke(this, data, continuation)
            if (result !== COROUTINE_SUSPENDED) continuation.resume(result as LightNovelRestoreOutcome)
        }

    private fun restoreLightNovelsMethod(): Method {
        return BackupRestorer::class.java.getDeclaredMethod(
            "restoreLightNovels",
            ByteArray::class.java,
            Continuation::class.java,
        ).apply { isAccessible = true }
    }

    private fun BackupRestorer.getRestoreProgressValue(): Int {
        val field = BackupRestorer::class.java.getDeclaredField("restoreProgress")
        field.isAccessible = true
        return field.getInt(this)
    }

    @Suppress("UNCHECKED_CAST")
    private fun BackupRestorer.getErrorCount(): Int {
        val field = BackupRestorer::class.java.getDeclaredField("errors")
        field.isAccessible = true
        val list = field.get(this) as MutableList<Pair<Date, String>>
        synchronized(list) {
            return list.size
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun BackupRestorer.getErrorMessages(): List<String> {
        val field = BackupRestorer::class.java.getDeclaredField("errors")
        field.isAccessible = true
        val list = field.get(this) as MutableList<Pair<Date, String>>
        synchronized(list) {
            return list.map { it.second }
        }
    }
}
