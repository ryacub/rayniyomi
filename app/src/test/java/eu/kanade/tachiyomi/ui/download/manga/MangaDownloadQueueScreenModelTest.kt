package eu.kanade.tachiyomi.ui.download.manga

import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

@OptIn(ExperimentalCoroutinesApi::class)
class MangaDownloadQueueScreenModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSource(id: Long): HttpSource = mockk<HttpSource>(relaxed = true).also {
        every { it.id } returns id
    }

    private fun createDownload(
        source: HttpSource,
        mangaId: Long,
        chapterId: Long,
    ): MangaDownload = MangaDownload(
        source = source,
        manga = Manga.create().copy(id = mangaId, title = "Manga $mangaId"),
        chapter = Chapter.create().copy(id = chapterId, name = "Chapter $chapterId"),
    )

    private fun createManager(downloads: List<MangaDownload> = emptyList()): MangaDownloadManager =
        mockk<MangaDownloadManager>(relaxed = true).also {
            every { it.queueState } returns MutableStateFlow(downloads)
            every { it.isDownloaderRunning } returns flowOf(false)
        }

    @Test
    fun `initial state is empty when queue is empty`() = runTest {
        val model = MangaDownloadQueueScreenModel(createManager())
        model.state.value shouldBe emptyList()
    }

    @Test
    fun `state groups downloads by source into headers`() = runTest {
        val source1 = createSource(1L)
        val source2 = createSource(2L)
        val downloads = listOf(
            createDownload(source1, 1L, 1L),
            createDownload(source1, 1L, 2L),
            createDownload(source2, 2L, 3L),
        )
        val model = MangaDownloadQueueScreenModel(createManager(downloads))

        model.state.value.size shouldBe 2
        model.state.value[0].downloads.size shouldBe 2
        model.state.value[1].downloads.size shouldBe 1
    }

    @Test
    fun `moveToTop moves download to first position within its source group`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 1L)
        val d2 = createDownload(source, 1L, 2L)
        val d3 = createDownload(source, 1L, 3L)
        val manager = createManager(listOf(d1, d2, d3))
        val model = MangaDownloadQueueScreenModel(manager)

        model.moveToTop(model.state.value[0].downloads[1]) // d2 at index 1
        advanceUntilIdle()

        val slot = slot<List<MangaDownload>>()
        coVerify { manager.reorderQueue(capture(slot)) }
        slot.captured.map { it.chapter.id } shouldContainExactly listOf(2L, 1L, 3L)
    }

    @Test
    fun `moveToTop does not reorder when download is already first`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 1L)
        val d2 = createDownload(source, 1L, 2L)
        val manager = createManager(listOf(d1, d2))
        val model = MangaDownloadQueueScreenModel(manager)

        model.moveToTop(model.state.value[0].downloads[0]) // d1 already first
        advanceUntilIdle()

        val slot = slot<List<MangaDownload>>()
        coVerify { manager.reorderQueue(capture(slot)) }
        slot.captured.map { it.chapter.id } shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `moveToBottom moves download to last position within its source group`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 1L)
        val d2 = createDownload(source, 1L, 2L)
        val d3 = createDownload(source, 1L, 3L)
        val manager = createManager(listOf(d1, d2, d3))
        val model = MangaDownloadQueueScreenModel(manager)

        model.moveToBottom(model.state.value[0].downloads[0]) // d1 at index 0
        advanceUntilIdle()

        val slot = slot<List<MangaDownload>>()
        coVerify { manager.reorderQueue(capture(slot)) }
        slot.captured.map { it.chapter.id } shouldContainExactly listOf(2L, 3L, 1L)
    }

    @Test
    fun `moveToTopSeries puts all manga downloads at front of flat queue`() = runTest {
        val source1 = createSource(1L)
        val source2 = createSource(2L)
        val d1 = createDownload(source1, 1L, 1L)
        val d2 = createDownload(source1, 1L, 2L)
        val d3 = createDownload(source2, 2L, 3L)
        val d4 = createDownload(source2, 2L, 4L)
        val manager = createManager(listOf(d1, d2, d3, d4))
        val model = MangaDownloadQueueScreenModel(manager)

        model.moveToTopSeries(2L) // manga2 downloads (d3, d4) to front
        advanceUntilIdle()

        val slot = slot<List<MangaDownload>>()
        coVerify { manager.reorderQueue(capture(slot)) }
        slot.captured.map { it.chapter.id } shouldContainExactly listOf(3L, 4L, 1L, 2L)
    }

    @Test
    fun `moveToBottomSeries puts all manga downloads at end of flat queue`() = runTest {
        val source1 = createSource(1L)
        val source2 = createSource(2L)
        val d1 = createDownload(source1, 1L, 1L)
        val d2 = createDownload(source1, 1L, 2L)
        val d3 = createDownload(source2, 2L, 3L)
        val d4 = createDownload(source2, 2L, 4L)
        val manager = createManager(listOf(d1, d2, d3, d4))
        val model = MangaDownloadQueueScreenModel(manager)

        model.moveToBottomSeries(1L) // manga1 downloads (d1, d2) to back
        advanceUntilIdle()

        val slot = slot<List<MangaDownload>>()
        coVerify { manager.reorderQueue(capture(slot)) }
        slot.captured.map { it.chapter.id } shouldContainExactly listOf(3L, 4L, 1L, 2L)
    }

    @Test
    fun `cancelDownload cancels single download`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 1L)
        val d2 = createDownload(source, 1L, 2L)
        val manager = createManager(listOf(d1, d2))
        val model = MangaDownloadQueueScreenModel(manager)

        model.cancelDownload(model.state.value[0].downloads[0])

        verify { manager.cancelQueuedDownloads(listOf(d1)) }
    }

    @Test
    fun `cancelSeries cancels all downloads for the given manga`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 1L)
        val d2 = createDownload(source, 1L, 2L)
        val d3 = createDownload(source, 2L, 3L) // different manga
        val manager = createManager(listOf(d1, d2, d3))
        val model = MangaDownloadQueueScreenModel(manager)

        model.cancelSeries(1L)

        val slot = slot<List<MangaDownload>>()
        verify { manager.cancelQueuedDownloads(capture(slot)) }
        slot.captured.map { it.chapter.id } shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `cancelSeries does nothing when no downloads match the manga`() = runTest {
        val source = createSource(1L)
        val manager = createManager(listOf(createDownload(source, 1L, 1L)))
        val model = MangaDownloadQueueScreenModel(manager)

        model.cancelSeries(999L)

        verify(exactly = 0) { manager.cancelQueuedDownloads(any()) }
    }

    @Test
    fun `reorderQueue sorts downloads by selector within each source group`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 3L)
        val d2 = createDownload(source, 1L, 1L)
        val d3 = createDownload(source, 1L, 2L)
        val manager = createManager(listOf(d1, d2, d3))
        val model = MangaDownloadQueueScreenModel(manager)

        model.reorderQueue(selector = { it.download.chapter.id })
        advanceUntilIdle()

        val slot = slot<List<MangaDownload>>()
        coVerify { manager.reorderQueue(capture(slot)) }
        slot.captured.map { it.chapter.id } shouldContainExactly listOf(1L, 2L, 3L)
    }

    @Test
    fun `reorderQueue with reverse=true sorts in descending order`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 1L)
        val d2 = createDownload(source, 1L, 2L)
        val d3 = createDownload(source, 1L, 3L)
        val manager = createManager(listOf(d1, d2, d3))
        val model = MangaDownloadQueueScreenModel(manager)

        model.reorderQueue(selector = { it.download.chapter.id }, reverse = true)
        advanceUntilIdle()

        val slot = slot<List<MangaDownload>>()
        coVerify { manager.reorderQueue(capture(slot)) }
        slot.captured.map { it.chapter.id } shouldContainExactly listOf(3L, 2L, 1L)
    }
}
