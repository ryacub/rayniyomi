package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

class AnimeDownloadManagerTest {

    private lateinit var context: Context
    private lateinit var mockNotifier: AnimeDownloadNotifier
    private lateinit var mockDownloader: AnimeDownloader
    private lateinit var manager: AnimeDownloadManager
    private lateinit var crashCountPref: Preference<Int>
    private var crashCount = 0

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        mockNotifier = mockk(relaxed = true)
        mockDownloader = mockk(relaxed = true) {
            every { start() } returns true
        }

        // InMemoryPreferenceStore creates a new Preference object each call, so set() doesn't
        // persist. Use a shared mock preference that actually tracks state.
        crashCount = 0
        crashCountPref = mockk {
            every { get() } answers { crashCount }
            every { set(any()) } answers { crashCount = firstArg() }
        }
        val downloadPreferences: DownloadPreferences = mockk(relaxed = true) {
            every { animeDownloadJobCrashCount() } returns crashCountPref
        }

        manager = AnimeDownloadManager(
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
    fun `downloaderStart resets non-zero crash count before start attempt`() {
        crashCount = 3

        val started = manager.downloaderStart()

        assertEquals(true, started)
        assertEquals(0, crashCountPref.get())
        verify(exactly = 1) { mockDownloader.start() }
    }

    @Test
    fun `downloaderStart leaves zero crash count unchanged`() {
        crashCount = 0

        val started = manager.downloaderStart()

        assertEquals(true, started)
        assertEquals(0, crashCountPref.get())
        verify(exactly = 1) { mockDownloader.start() }
    }

    @Test
    fun `downloaderStart keeps reset state when start attempt throws`() {
        crashCount = 2
        every { mockDownloader.start() } throws RuntimeException("start failed")

        assertThrows(RuntimeException::class.java) {
            manager.downloaderStart()
        }

        assertEquals(0, crashCountPref.get())
        verify(exactly = 1) { mockDownloader.start() }
    }

    @Test
    fun `deleteAnime removeQueued serializes with reorder and prevents stale resurrection`() = runTest {
        val targetAnime = Anime.create().copy(id = 1L, source = 100L, title = "Target")
        val otherAnime = Anime.create().copy(id = 2L, source = 100L, title = "Other")
        val source = mockk<AnimeHttpSource>(relaxed = true)

        val targetDownload = AnimeDownload(
            source = source,
            anime = targetAnime,
            episode = Episode.create().copy(id = 11L, animeId = targetAnime.id, name = "T"),
        )
        val otherDownload = AnimeDownload(
            source = source,
            anime = otherAnime,
            episode = Episode.create().copy(id = 22L, animeId = otherAnime.id, name = "O"),
        )

        val queueState = MutableStateFlow(listOf(targetDownload, otherDownload))
        val removeEntered = CompletableDeferred<Unit>()
        val allowRemove = CompletableDeferred<Unit>()

        every { mockDownloader.queueState } returns queueState
        every { mockDownloader.updateQueue(any()) } answers {
            queueState.value = firstArg()
        }
        every { mockDownloader.removeFromQueueByEpisodeIds(any()) } answers {
            removeEntered.complete(Unit)
            runBlocking { allowRemove.await() }
            val ids = firstArg<List<Long>>().toSet()
            queueState.value = queueState.value.filterNot { it.episode.id in ids }
        }

        val localManager = AnimeDownloadManager(
            context = context,
            storageManager = mockk(relaxed = true),
            provider = mockk(relaxed = true) {
                every { findAnimeDir(any(), any()) } returns null
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
            localManager.deleteAnime(targetAnime, source, removeQueued = true)
            removeEntered.await()

            val reorderJob = async {
                localManager.reorderQueueByEpisodeIds(staleSnapshot.mapNotNull { it.episode.id })
            }

            val reorderFinishedEarly = withTimeoutOrNull(50) { reorderJob.await() }
            assertNull(reorderFinishedEarly, "reorderQueue should block while delete-associated removal is active")

            allowRemove.complete(Unit)
            reorderJob.await()

            val queuedAnimeIds = queueState.value.map { it.anime.id }.toSet()
            assertEquals(setOf(otherAnime.id), queuedAnimeIds)
        } finally {
            localManager.close()
        }
    }

    @Test
    fun `deleteAnime with removeQueued false does not touch queue`() = runTest {
        val anime = Anime.create().copy(id = 1L, source = 100L, title = "Target")
        val source = mockk<AnimeHttpSource>(relaxed = true)
        val deleteEntered = CompletableDeferred<Unit>()
        every { mockDownloader.removeFromQueueByEpisodeIds(any()) } answers {}

        val localManager = AnimeDownloadManager(
            context = context,
            storageManager = mockk(relaxed = true),
            provider = mockk(relaxed = true) {
                every { findAnimeDir(any(), any()) } answers {
                    deleteEntered.complete(Unit)
                    null
                }
                every { findSourceDir(any()) } returns null
            },
            cache = mockk(relaxed = true),
            getCategories = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            downloadPreferences = mockk(relaxed = true),
            downloaderForTesting = mockDownloader,
        )

        try {
            localManager.deleteAnime(anime, source, removeQueued = false)
            deleteEntered.await()
            verify(exactly = 0) { mockDownloader.removeFromQueueByEpisodeIds(any()) }
        } finally {
            localManager.close()
        }
    }

    @Test
    fun `deleteAnime queue removal failure does not deadlock subsequent reorder`() = runTest {
        val anime = Anime.create().copy(id = 1L, source = 100L, title = "Target")
        val source = mockk<AnimeHttpSource>(relaxed = true)
        val removeAttempted = CompletableDeferred<Unit>()
        val queueState = MutableStateFlow(
            listOf(
                AnimeDownload(
                    source = source,
                    anime = anime,
                    episode = Episode.create().copy(id = 11L, animeId = anime.id, name = "T"),
                ),
            ),
        )

        every { mockDownloader.queueState } returns queueState
        every { mockDownloader.updateQueue(any()) } answers {
            queueState.value = firstArg()
        }
        every { mockDownloader.removeFromQueueByEpisodeIds(any()) } answers {
            removeAttempted.complete(Unit)
            throw RuntimeException("remove failed")
        }

        val localManager = AnimeDownloadManager(
            context = context,
            storageManager = mockk(relaxed = true),
            provider = mockk(relaxed = true) {
                every { findAnimeDir(any(), any()) } returns null
                every { findSourceDir(any()) } returns null
            },
            cache = mockk(relaxed = true),
            getCategories = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            downloadPreferences = mockk(relaxed = true),
            downloaderForTesting = mockDownloader,
        )

        try {
            localManager.deleteAnime(anime, source, removeQueued = true)
            removeAttempted.await()
            val reorderCompleted = withTimeoutOrNull(500) {
                localManager.reorderQueueByEpisodeIds(queueState.value.mapNotNull { it.episode.id })
                Unit
            }
            assertEquals(Unit, reorderCompleted, "reorderQueue should not deadlock after delete failure")
        } finally {
            localManager.close()
        }
    }
}
