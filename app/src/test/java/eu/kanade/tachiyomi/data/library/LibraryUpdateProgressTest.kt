package eu.kanade.tachiyomi.data.library

import android.content.Context
import androidx.work.Data
import androidx.work.Operation
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.google.common.util.concurrent.ListenableFuture
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.ContinuationInterceptor

class LibraryUpdateProgressTest {

    @Test
    fun `running work exposes progress and ended work clears it`() {
        val longTitle = "x".repeat(300)
        val progress = LibraryUpdateProgress(
            activeTitles = listOf("Current title", longTitle),
            completed = 2,
            total = 5,
        )
        val data = progress.toWorkData()
        val finished = workInfo(WorkInfo.State.SUCCEEDED, data)
        val running = workInfo(WorkInfo.State.RUNNING, data)
        val starting = workInfo(WorkInfo.State.RUNNING)

        assertEquals(
            LibraryUpdateProgress(
                activeTitles = listOf("Current title", longTitle.take(256)),
                completed = 2,
                total = 5,
            ),
            listOf(finished, running).toLibraryUpdateProgressOrNull(),
        )
        assertEquals(
            LibraryUpdateProgress(activeTitles = emptyList(), completed = 0, total = 0),
            listOf(starting).toLibraryUpdateProgressOrNull(),
        )
        assertNull(listOf(finished).toLibraryUpdateProgressOrNull())
    }

    @Test
    fun `parallel completions publish counts in order`() = runTest {
        val firstCompletionPublished = CompletableDeferred<Unit>()
        val releaseFirstCompletion = CompletableDeferred<Unit>()
        val publishedCounts = mutableListOf<Int>()
        val tracker = LibraryUpdateProgressTracker<String>(
            total = 2,
            title = { it },
            onProgress = { _, progress ->
                if (progress.completed == 1 && !firstCompletionPublished.isCompleted) {
                    firstCompletionPublished.complete(Unit)
                    releaseFirstCompletion.await()
                }
                if (progress.completed > 0) publishedCounts += progress.completed
            },
        )

        tracker.entryStarted("First")
        tracker.entryStarted("Second")
        val first = launch { tracker.entryCompleted("First") }
        firstCompletionPublished.await()
        val second = launch { tracker.entryCompleted("Second") }
        runCurrent()

        assertFalse(second.isCompleted)
        releaseFirstCompletion.complete(Unit)
        first.join()
        second.join()

        assertEquals(listOf(1, 2), publishedCounts)
    }

    @Test
    fun `cancel stops each library work on a dispatcher outside the UI`() = runTest {
        val uiDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(uiDispatcher)
        val context = mockk<Context>()
        val mangaWorkId = UUID.randomUUID()
        val animeWorkId = UUID.randomUUID()
        val runningWork = listOf(
            WorkInfo(mangaWorkId, WorkInfo.State.RUNNING, setOf(MangaLibraryUpdateJob.TAG)),
            WorkInfo(animeWorkId, WorkInfo.State.RUNNING, setOf(AnimeLibraryUpdateJob.TAG)),
        )
        val workManager = mockk<WorkManager>()
        val summaryStore = mockk<LibraryUpdateSummaryStore>(relaxed = true)
        val queriedTags = CopyOnWriteArrayList<Set<String>>()
        val canceledWorkIds = CopyOnWriteArrayList<UUID>()
        var mangaStoppedOnIo = false
        var animeStoppedOnIo = false

        mockkObject(WorkManager.Companion)
        try {
            every { WorkManager.Companion.getInstance(any<Context>()) } returns workManager
            every { workManager.getWorkInfos(any<WorkQuery>()) } answers {
                val query = firstArg<WorkQuery>()
                queriedTags.add(query.tags.toSet())
                val matches = runningWork.filter { workInfo ->
                    (query.tags.isEmpty() || query.tags.any(workInfo.tags::contains)) &&
                        (query.states.isEmpty() || workInfo.state in query.states)
                }
                future(matches)
            }
            every { workManager.cancelWorkById(any()) } answers {
                canceledWorkIds.add(firstArg<UUID>())
                mockk<Operation>(relaxed = true)
            }

            val mangaCancellation = launchLibraryUpdateCancellation {
                mangaStoppedOnIo = currentCoroutineContext()[ContinuationInterceptor] === Dispatchers.IO
                MangaLibraryUpdateJob.stop(context, summaryStore)
            }
            val animeCancellation = launchLibraryUpdateCancellation {
                animeStoppedOnIo = currentCoroutineContext()[ContinuationInterceptor] === Dispatchers.IO
                AnimeLibraryUpdateJob.stop(context, summaryStore)
            }
            mangaCancellation.join()
            animeCancellation.join()

            assertEquals(
                setOf(setOf(MangaLibraryUpdateJob.TAG), setOf(AnimeLibraryUpdateJob.TAG)),
                queriedTags.toSet(),
            )
            assertEquals(setOf(mangaWorkId, animeWorkId), canceledWorkIds.toSet())
            verify {
                summaryStore.requestUserCancellation(LibraryUpdateMedia.MANGA, mangaWorkId.toString())
                summaryStore.requestUserCancellation(LibraryUpdateMedia.ANIME, animeWorkId.toString())
            }
            assertTrue(mangaStoppedOnIo)
            assertTrue(animeStoppedOnIo)
        } finally {
            unmockkObject(WorkManager.Companion)
            Dispatchers.resetMain()
        }
    }

    private fun workInfo(state: WorkInfo.State, progress: Data = Data.EMPTY): WorkInfo = mockk {
        every { this@mockk.state } returns state
        every { this@mockk.progress } returns progress
    }

    private fun future(workInfos: List<WorkInfo>): ListenableFuture<List<WorkInfo>> = mockk {
        every { get() } returns workInfos
    }
}
