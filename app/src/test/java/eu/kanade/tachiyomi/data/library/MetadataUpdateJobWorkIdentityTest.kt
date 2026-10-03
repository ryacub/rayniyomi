package eu.kanade.tachiyomi.data.library

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.google.common.util.concurrent.ListenableFuture
import eu.kanade.tachiyomi.data.library.anime.AnimeMetadataUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaMetadataUpdateJob
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The anime and manga metadata jobs start from the same settings action, so
 * each job needs its own WorkManager tag and unique work name.
 */
class MetadataUpdateJobWorkIdentityTest {

    private val context = mockk<Context>()
    private lateinit var workManager: FakeWorkManager

    @BeforeEach
    fun setUp() {
        workManager = FakeWorkManager()
        mockkObject(WorkManager.Companion)
        every { WorkManager.Companion.getInstance(any<Context>()) } returns workManager.mock
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    @Test
    fun `settings action enqueues both the anime and the manga job`() {
        AnimeMetadataUpdateJob.startNow(context) shouldBe true
        MangaMetadataUpdateJob.startNow(context) shouldBe true

        workManager.entries shouldHaveSize 2
        workManager.entries[0].name shouldNotBe workManager.entries[1].name
    }

    @Test
    fun `manga job starts while the anime job runs`() {
        AnimeMetadataUpdateJob.startNow(context)
        workManager.entries.single().state = WorkInfo.State.RUNNING

        MangaMetadataUpdateJob.startNow(context) shouldBe true

        workManager.entries shouldHaveSize 2
    }

    @Test
    fun `second start of a running job does not enqueue a duplicate`() {
        AnimeMetadataUpdateJob.startNow(context)
        MangaMetadataUpdateJob.startNow(context)
        workManager.entries.forEach { it.state = WorkInfo.State.RUNNING }

        AnimeMetadataUpdateJob.startNow(context) shouldBe false
        MangaMetadataUpdateJob.startNow(context) shouldBe false

        workManager.entries shouldHaveSize 2
    }

    @Test
    fun `second start of an enqueued job keeps the existing work`() {
        AnimeMetadataUpdateJob.startNow(context)
        AnimeMetadataUpdateJob.startNow(context)

        workManager.entries shouldHaveSize 1
    }

    @Test
    fun `stopping the anime job does not cancel the manga job`() {
        val (anime, manga) = startBothRunning()

        AnimeMetadataUpdateJob.stop(context)

        anime.state shouldBe WorkInfo.State.CANCELLED
        manga.state shouldBe WorkInfo.State.RUNNING
    }

    @Test
    fun `stopping the manga job does not cancel the anime job`() {
        val (anime, manga) = startBothRunning()

        MangaMetadataUpdateJob.stop(context)

        anime.state shouldBe WorkInfo.State.RUNNING
        manga.state shouldBe WorkInfo.State.CANCELLED
    }

    private fun startBothRunning(): Pair<FakeWorkManager.Entry, FakeWorkManager.Entry> {
        AnimeMetadataUpdateJob.startNow(context)
        MangaMetadataUpdateJob.startNow(context)
        workManager.entries shouldHaveSize 2
        workManager.entries.forEach { it.state = WorkInfo.State.RUNNING }
        return workManager.entries[0] to workManager.entries[1]
    }

    /**
     * Models the WorkManager calls the metadata jobs make: unique enqueue with
     * the KEEP policy, tag lookup, tag and state query, and cancel by ID.
     */
    private class FakeWorkManager {
        class Entry(val name: String, val request: OneTimeWorkRequest, var state: WorkInfo.State)

        val entries = mutableListOf<Entry>()
        val mock = mockk<WorkManager>()

        init {
            every { mock.enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>()) } answers {
                val name = firstArg<String>()
                val policy = secondArg<ExistingWorkPolicy>()
                val hasUnfinished = entries.any { it.name == name && !it.state.isFinished }
                if (policy != ExistingWorkPolicy.KEEP || !hasUnfinished) {
                    entries += Entry(name, thirdArg(), WorkInfo.State.ENQUEUED)
                }
                mockk(relaxed = true)
            }
            every { mock.getWorkInfosByTag(any()) } answers {
                val tag = firstArg<String>()
                future(entries.filter { tag in it.request.tags })
            }
            every { mock.getWorkInfos(any<WorkQuery>()) } answers {
                val query = firstArg<WorkQuery>()
                future(
                    entries.filter { entry ->
                        (query.tags.isEmpty() || query.tags.any { it in entry.request.tags }) &&
                            (query.states.isEmpty() || entry.state in query.states)
                    },
                )
            }
            every { mock.cancelWorkById(any()) } answers {
                val id = firstArg<UUID>()
                entries.first { it.request.id == id }.state = WorkInfo.State.CANCELLED
                mockk(relaxed = true)
            }
        }

        private fun future(matches: List<Entry>): ListenableFuture<List<WorkInfo>> {
            val infos = matches.map { WorkInfo(it.request.id, it.state, it.request.tags) }
            return mockk { every { get() } returns infos }
        }
    }
}
