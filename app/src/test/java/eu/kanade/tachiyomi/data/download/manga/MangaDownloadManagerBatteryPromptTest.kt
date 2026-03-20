@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import eu.kanade.tachiyomi.data.download.core.BatteryOptimizationChecker
import eu.kanade.tachiyomi.data.download.core.BatteryOptimizationPromptRequest
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.storage.service.StorageManager
import uy.kohesive.injekt.Injekt

class MangaDownloadManagerBatteryPromptTest {

    private lateinit var context: Context
    private lateinit var downloadPreferences: DownloadPreferences
    private lateinit var batteryOptimizationChecker: BatteryOptimizationChecker
    private lateinit var storageManager: StorageManager
    private lateinit var provider: MangaDownloadProvider
    private lateinit var cache: MangaDownloadCache
    private lateinit var getCategories: GetMangaCategories
    private lateinit var sourceManager: MangaSourceManager
    private lateinit var manager: MangaDownloadManager
    private lateinit var batteryPromptFlow: SharedFlow<BatteryOptimizationPromptRequest>
    private lateinit var downloader: MangaDownloader

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        downloadPreferences = mockk(relaxed = true)
        batteryOptimizationChecker = mockk()
        storageManager = mockk(relaxed = true)
        provider = mockk(relaxed = true)
        cache = mockk(relaxed = true)
        getCategories = mockk(relaxed = true)
        sourceManager = mockk(relaxed = true)

        // Mock the batteryOptimizationPromptShown preference to return a stateful mock
        val promptShownPreference = createStatefulBooleanPreference(false)
        every { downloadPreferences.batteryOptimizationPromptShown() } returns promptShownPreference

        // Create a fully mocked MangaDownloader without calling the real constructor
        downloader = mockk(relaxed = true) {
            every { queueState } returns MutableStateFlow(emptyList())
            every { queueChapters(any(), any(), any()) } returns Unit
            every { moveToFront(any()) } returns Unit
            every { updateQueue(any()) } returns Unit
            every { addToStartOfQueue(any()) } returns Unit
            every { pause() } returns Unit
            every { start() } returns false
            every { stop() } returns Unit
            every { isRunning } returns false
        }
    }

    @AfterEach
    fun tearDown() {
        // No cleanup needed
    }

    @Test
    fun `flow emits when downloadChapters called with 10 or more items and optimization enabled and not shown`() = runTest {
        // Arrange
        every { batteryOptimizationChecker.isOptimizationEnabled() } returns true

        val manga = mockk<Manga>()
        val chapters = createChapters(count = 10)
        val emittedRequests = mutableListOf<BatteryOptimizationPromptRequest>()

        // Create manager with test scope
        manager = MangaDownloadManager(
            context = context,
            storageManager = storageManager,
            provider = provider,
            cache = cache,
            getCategories = getCategories,
            sourceManager = sourceManager,
            downloadPreferences = downloadPreferences,
            batteryOptimizationChecker = batteryOptimizationChecker,
            downloaderForTesting = downloader,
            scopeForTesting = CoroutineScope(coroutineContext),
        )

        batteryPromptFlow = manager.batteryOptimizationPromptFlow

        // Act & Assert
        val collectorJob = async {
            batteryPromptFlow.collect { request ->
                emittedRequests.add(request)
            }
        }

        manager.downloadChapters(manga, chapters)

        // Give the flow time to emit
        advanceUntilIdle()
        collectorJob.cancel()

        emittedRequests.size shouldBe 1
    }

    @Test
    fun `flow does not emit when fewer than 10 items queued`() = runTest {
        // Arrange
        every { batteryOptimizationChecker.isOptimizationEnabled() } returns true

        val manga = mockk<Manga>()
        val chapters = createChapters(count = 9)
        val emittedRequests = mutableListOf<BatteryOptimizationPromptRequest>()

        // Create manager with test scope
        manager = MangaDownloadManager(
            context = context,
            storageManager = storageManager,
            provider = provider,
            cache = cache,
            getCategories = getCategories,
            sourceManager = sourceManager,
            downloadPreferences = downloadPreferences,
            batteryOptimizationChecker = batteryOptimizationChecker,
            downloaderForTesting = downloader,
            scopeForTesting = CoroutineScope(coroutineContext),
        )

        batteryPromptFlow = manager.batteryOptimizationPromptFlow

        // Act & Assert
        val collectorJob = async {
            batteryPromptFlow.collect { request ->
                emittedRequests.add(request)
            }
        }

        manager.downloadChapters(manga, chapters)

        // Give the flow time to emit (or not)
        advanceUntilIdle()
        collectorJob.cancel()

        emittedRequests shouldBe emptyList()
    }

    @Test
    fun `flow does not emit when optimization is disabled (app is exempted)`() = runTest {
        // Arrange
        every { batteryOptimizationChecker.isOptimizationEnabled() } returns false

        val manga = mockk<Manga>()
        val chapters = createChapters(count = 10)
        val emittedRequests = mutableListOf<BatteryOptimizationPromptRequest>()

        // Create manager with test scope
        manager = MangaDownloadManager(
            context = context,
            storageManager = storageManager,
            provider = provider,
            cache = cache,
            getCategories = getCategories,
            sourceManager = sourceManager,
            downloadPreferences = downloadPreferences,
            batteryOptimizationChecker = batteryOptimizationChecker,
            downloaderForTesting = downloader,
            scopeForTesting = CoroutineScope(coroutineContext),
        )

        batteryPromptFlow = manager.batteryOptimizationPromptFlow

        // Act & Assert
        val collectorJob = async {
            batteryPromptFlow.collect { request ->
                emittedRequests.add(request)
            }
        }

        manager.downloadChapters(manga, chapters)

        // Give the flow time to emit (or not)
        advanceUntilIdle()
        collectorJob.cancel()

        emittedRequests shouldBe emptyList()
    }

    @Test
    fun `flow does not emit when prompt already shown this session`() = runTest {
        // Arrange
        every { batteryOptimizationChecker.isOptimizationEnabled() } returns true

        // Mock preference as already shown
        val promptShownPreference = createStatefulBooleanPreference(true)
        every { downloadPreferences.batteryOptimizationPromptShown() } returns promptShownPreference

        val manga = mockk<Manga>()
        val chapters = createChapters(count = 10)
        val emittedRequests = mutableListOf<BatteryOptimizationPromptRequest>()

        // Create manager with test scope
        manager = MangaDownloadManager(
            context = context,
            storageManager = storageManager,
            provider = provider,
            cache = cache,
            getCategories = getCategories,
            sourceManager = sourceManager,
            downloadPreferences = downloadPreferences,
            batteryOptimizationChecker = batteryOptimizationChecker,
            downloaderForTesting = downloader,
            scopeForTesting = CoroutineScope(coroutineContext),
        )

        batteryPromptFlow = manager.batteryOptimizationPromptFlow

        // Act & Assert
        val collectorJob = async {
            batteryPromptFlow.collect { request ->
                emittedRequests.add(request)
            }
        }

        manager.downloadChapters(manga, chapters)

        // Give the flow time to emit (or not)
        advanceUntilIdle()
        collectorJob.cancel()

        emittedRequests shouldBe emptyList()
    }

    @Test
    fun `flow emits at most once even with multiple rapid calls`() = runTest {
        // Arrange
        every { batteryOptimizationChecker.isOptimizationEnabled() } returns true

        val manga = mockk<Manga>()
        val chapters = createChapters(count = 10)
        val emittedRequests = mutableListOf<BatteryOptimizationPromptRequest>()

        // Create manager with test scope
        manager = MangaDownloadManager(
            context = context,
            storageManager = storageManager,
            provider = provider,
            cache = cache,
            getCategories = getCategories,
            sourceManager = sourceManager,
            downloadPreferences = downloadPreferences,
            batteryOptimizationChecker = batteryOptimizationChecker,
            downloaderForTesting = downloader,
            scopeForTesting = CoroutineScope(coroutineContext),
        )

        batteryPromptFlow = manager.batteryOptimizationPromptFlow

        // Act & Assert
        val collectorJob = async {
            batteryPromptFlow.collect { request ->
                emittedRequests.add(request)
            }
        }

        // Multiple rapid calls
        manager.downloadChapters(manga, chapters)
        manager.downloadChapters(manga, chapters)
        manager.downloadChapters(manga, chapters)

        // Give the flow time to emit
        advanceUntilIdle()
        collectorJob.cancel()

        // Should emit exactly once, then subsequent calls do not emit (preference is marked as shown)
        emittedRequests.size shouldBe 1
    }

    /**
     * Helper to create a stateful Boolean preference mock.
     * Uses the pattern from project MEMORY.md to handle InMemoryPreferenceStore limitation.
     */
    private fun createStatefulBooleanPreference(initialValue: Boolean): Preference<Boolean> {
        var currentValue = initialValue
        return mockk {
            every { get() } answers { currentValue }
            every { set(any()) } answers { currentValue = firstArg() }
        }
    }

    /**
     * Helper to create Chapter objects for testing.
     */
    private fun createChapters(count: Int): List<Chapter> {
        return (1..count).map { index ->
            mockk<Chapter> {
                every { id } returns index.toLong()
                every { name } returns "Chapter $index"
                every { scanlator } returns null
            }
        }
    }
}
