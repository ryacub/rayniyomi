package eu.kanade.tachiyomi.data.backup.restore

import android.content.Context
import eu.kanade.tachiyomi.data.backup.BackupNotifier
import eu.kanade.tachiyomi.data.backup.lightnovel.LightNovelBackupDataSource
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.AnimeRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.CustomButtonRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.ExtensionsRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaCategoriesRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaExtensionRepoRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.MangaRestorer
import eu.kanade.tachiyomi.data.backup.restore.restorers.PreferenceRestorer
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Date

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

    private fun createRestorer(notifier: BackupNotifier = mockk(relaxed = true)): BackupRestorer {
        return BackupRestorer(
            context = mockk<Context>(relaxed = true),
            notifier = notifier,
            isSync = false,
            animeCategoriesRestorer = mockk<AnimeCategoriesRestorer>(relaxed = true),
            mangaCategoriesRestorer = mockk<MangaCategoriesRestorer>(relaxed = true),
            preferenceRestorer = mockk<PreferenceRestorer>(relaxed = true),
            animeExtensionRepoRestorer = mockk<AnimeExtensionRepoRestorer>(relaxed = true),
            mangaExtensionRepoRestorer = mockk<MangaExtensionRepoRestorer>(relaxed = true),
            customButtonRestorer = mockk<CustomButtonRestorer>(relaxed = true),
            animeRestorer = mockk<AnimeRestorer>(relaxed = true),
            mangaRestorer = mockk<MangaRestorer>(relaxed = true),
            extensionsRestorer = mockk<ExtensionsRestorer>(relaxed = true),
            lightNovelBackupDataSource = mockk<LightNovelBackupDataSource>(relaxed = true),
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
}
