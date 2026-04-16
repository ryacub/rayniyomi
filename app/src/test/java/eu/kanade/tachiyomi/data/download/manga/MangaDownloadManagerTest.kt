package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

class MangaDownloadManagerTest {

    private lateinit var context: Context
    private lateinit var mockDownloader: MangaDownloader
    private lateinit var mockNotifier: MangaDownloadNotifier
    private lateinit var manager: MangaDownloadManager

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        mockDownloader = mockk(relaxed = true)
        mockNotifier = mockk(relaxed = true)

        // InMemoryPreferenceStore creates a new Preference object each call, so set() doesn't
        // persist. Use a shared mock preference that actually tracks state.
        var crashCount = 0
        val crashCountPref: Preference<Int> = mockk {
            every { get() } answers { crashCount }
            every { set(any()) } answers { crashCount = firstArg() }
        }
        val downloadPreferences: DownloadPreferences = mockk(relaxed = true) {
            every { mangaDownloadJobCrashCount() } returns crashCountPref
        }

        manager = MangaDownloadManager(
            context = context,
            storageManager = mockk(relaxed = true),
            provider = mockk(relaxed = true),
            cache = mockk(relaxed = true),
            getCategories = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            downloadPreferences = downloadPreferences,
            downloaderForTesting = mockDownloader,
        )
        manager.notifier = mockNotifier
    }

    @Test
    fun `incrementJobCrashCount does not show notification below threshold`() {
        // Two crashes — count reaches 2, below threshold of 3
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()

        verify(exactly = 0) { mockNotifier.onCrashThresholdExceeded() }
    }

    @Test
    fun `incrementJobCrashCount shows notification when threshold is reached`() {
        // Three crashes — count reaches 3 (threshold)
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()

        verify { mockNotifier.onCrashThresholdExceeded() }
    }

    @Test
    fun `incrementJobCrashCount shows notification on each call above threshold`() {
        // Four crashes — fourth is above threshold, should also notify
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()

        verify(atLeast = 2) { mockNotifier.onCrashThresholdExceeded() }
    }

    @Test
    fun `downloaderStart clears crash state and notification when start succeeds`() {
        every { mockDownloader.start() } returns true

        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()

        manager.downloaderStart()

        verify(exactly = 1) { mockNotifier.cancelCrashNotification() }
    }

    @Test
    fun `downloaderStart does not cancel crash notification when crash count is zero`() {
        every { mockDownloader.start() } returns true

        manager.downloaderStart()

        verify(exactly = 0) { mockNotifier.cancelCrashNotification() }
    }

    @Test
    fun `downloaderStart does not clear crash state when downloader fails to start`() {
        every { mockDownloader.start() } returns false

        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()

        manager.downloaderStart()

        verify(exactly = 0) { mockNotifier.cancelCrashNotification() }
    }

    @Test
    fun `deleteManga removeQueued serializes with reorder and prevents stale resurrection`() = runTest {
        val targetManga = Manga.create().copy(id = 1L, source = 100L, title = "Target")
        val otherManga = Manga.create().copy(id = 2L, source = 100L, title = "Other")
        val source = mockk<HttpSource>(relaxed = true)

        val targetDownload = MangaDownload(
            source = source,
            manga = targetManga,
            chapter = Chapter.create().copy(id = 11L, mangaId = targetManga.id, name = "T"),
        )
        val otherDownload = MangaDownload(
            source = source,
            manga = otherManga,
            chapter = Chapter.create().copy(id = 22L, mangaId = otherManga.id, name = "O"),
        )

        val queueState = MutableStateFlow(listOf(targetDownload, otherDownload))
        val removeEntered = CompletableDeferred<Unit>()
        val allowRemove = CompletableDeferred<Unit>()

        every { mockDownloader.queueState } returns queueState
        every { mockDownloader.updateQueue(any()) } answers {
            queueState.value = firstArg()
        }
        every { mockDownloader.removeFromQueueByChapterIds(any()) } answers {
            removeEntered.complete(Unit)
            runBlocking { allowRemove.await() }
            val ids = firstArg<List<Long>>().toSet()
            queueState.value = queueState.value.filterNot { it.chapter.id in ids }
        }

        val localManager = MangaDownloadManager(
            context = context,
            storageManager = mockk(relaxed = true),
            provider = mockk(relaxed = true) {
                every { findMangaDir(any(), any()) } returns null
                every { findSourceDir(any()) } returns null
            },
            cache = mockk(relaxed = true),
            getCategories = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            downloadPreferences = mockk(relaxed = true),
            downloaderForTesting = mockDownloader,
        )

        try {
            val staleSnapshot = listOf(targetDownload, otherDownload)
            localManager.deleteManga(targetManga, source, removeQueued = true)
            removeEntered.await()

            val reorderJob = async {
                localManager.reorderQueue(staleSnapshot)
            }

            val reorderFinishedEarly = withTimeoutOrNull(50) { reorderJob.await() }
            assertNull(reorderFinishedEarly, "reorderQueue should block while delete-associated removal is active")

            allowRemove.complete(Unit)
            reorderJob.await()

            val queuedMangaIds = queueState.value.map { it.manga.id }.toSet()
            assertEquals(setOf(otherManga.id), queuedMangaIds)
        } finally {
            localManager.close()
        }
    }
}
