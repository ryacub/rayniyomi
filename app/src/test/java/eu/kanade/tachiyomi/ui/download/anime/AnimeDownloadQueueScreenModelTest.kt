package eu.kanade.tachiyomi.ui.download.anime

import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
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
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeDownloadQueueScreenModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSource(id: Long): AnimeHttpSource = mockk<AnimeHttpSource>(relaxed = true).also {
        every { it.id } returns id
    }

    private fun createDownload(
        source: AnimeHttpSource,
        animeId: Long,
        episodeId: Long,
    ): AnimeDownload = AnimeDownload(
        source = source,
        anime = Anime.create().copy(id = animeId, title = "Anime $animeId"),
        episode = Episode.create().copy(id = episodeId, name = "Episode $episodeId"),
    )

    private fun createManager(downloads: List<AnimeDownload> = emptyList()): AnimeDownloadManager =
        mockk<AnimeDownloadManager>(relaxed = true).also {
            every { it.queueState } returns MutableStateFlow(downloads)
            every { it.isDownloaderRunning } returns flowOf(false)
        }

    @Test
    fun `initial state is empty when queue is empty`() = runTest {
        val model = AnimeDownloadQueueScreenModel(createManager())
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
        val model = AnimeDownloadQueueScreenModel(createManager(downloads))

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
        val model = AnimeDownloadQueueScreenModel(manager)

        model.moveToTop(model.state.value[0].downloads[1]) // d2 at index 1
        advanceUntilIdle()

        val slot = slot<List<Long>>()
        coVerify { manager.reorderQueueByEpisodeIds(capture(slot)) }
        slot.captured shouldContainExactly listOf(2L, 1L, 3L)
    }

    @Test
    fun `moveToTop does not reorder when download is already first`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 1L)
        val d2 = createDownload(source, 1L, 2L)
        val manager = createManager(listOf(d1, d2))
        val model = AnimeDownloadQueueScreenModel(manager)

        model.moveToTop(model.state.value[0].downloads[0]) // d1 already first
        advanceUntilIdle()

        val slot = slot<List<Long>>()
        coVerify { manager.reorderQueueByEpisodeIds(capture(slot)) }
        slot.captured shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `moveToBottom moves download to last position within its source group`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 1L)
        val d2 = createDownload(source, 1L, 2L)
        val d3 = createDownload(source, 1L, 3L)
        val manager = createManager(listOf(d1, d2, d3))
        val model = AnimeDownloadQueueScreenModel(manager)

        model.moveToBottom(model.state.value[0].downloads[0]) // d1 at index 0
        advanceUntilIdle()

        val slot = slot<List<Long>>()
        coVerify { manager.reorderQueueByEpisodeIds(capture(slot)) }
        slot.captured shouldContainExactly listOf(2L, 3L, 1L)
    }

    @Test
    fun `moveToTopSeries puts all anime downloads at front of flat queue`() = runTest {
        val source1 = createSource(1L)
        val source2 = createSource(2L)
        val d1 = createDownload(source1, 1L, 1L)
        val d2 = createDownload(source1, 1L, 2L)
        val d3 = createDownload(source2, 2L, 3L)
        val d4 = createDownload(source2, 2L, 4L)
        val manager = createManager(listOf(d1, d2, d3, d4))
        val model = AnimeDownloadQueueScreenModel(manager)

        model.moveToTopSeries(2L) // anime2 downloads (d3, d4) to front
        advanceUntilIdle()

        val slot = slot<List<Long>>()
        coVerify { manager.reorderQueueByEpisodeIds(capture(slot)) }
        slot.captured shouldContainExactly listOf(3L, 4L, 1L, 2L)
    }

    @Test
    fun `moveToBottomSeries puts all anime downloads at end of flat queue`() = runTest {
        val source1 = createSource(1L)
        val source2 = createSource(2L)
        val d1 = createDownload(source1, 1L, 1L)
        val d2 = createDownload(source1, 1L, 2L)
        val d3 = createDownload(source2, 2L, 3L)
        val d4 = createDownload(source2, 2L, 4L)
        val manager = createManager(listOf(d1, d2, d3, d4))
        val model = AnimeDownloadQueueScreenModel(manager)

        model.moveToBottomSeries(1L) // anime1 downloads (d1, d2) to back
        advanceUntilIdle()

        val slot = slot<List<Long>>()
        coVerify { manager.reorderQueueByEpisodeIds(capture(slot)) }
        slot.captured shouldContainExactly listOf(3L, 4L, 1L, 2L)
    }

    @Test
    fun `cancelDownload cancels single download`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 1L)
        val d2 = createDownload(source, 1L, 2L)
        val manager = createManager(listOf(d1, d2))
        val model = AnimeDownloadQueueScreenModel(manager)

        model.cancelDownload(model.state.value[0].downloads[0])

        verify { manager.cancelQueuedDownloads(listOf(d1)) }
    }

    @Test
    fun `cancelSeries cancels all downloads for the given anime`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 1L)
        val d2 = createDownload(source, 1L, 2L)
        val d3 = createDownload(source, 2L, 3L) // different anime
        val manager = createManager(listOf(d1, d2, d3))
        val model = AnimeDownloadQueueScreenModel(manager)

        model.cancelSeries(1L)

        val slot = slot<List<AnimeDownload>>()
        verify { manager.cancelQueuedDownloads(capture(slot)) }
        slot.captured.map { it.episode.id } shouldContainExactly listOf(1L, 2L)
    }

    @Test
    fun `cancelSeries does nothing when no downloads match the anime`() = runTest {
        val source = createSource(1L)
        val manager = createManager(listOf(createDownload(source, 1L, 1L)))
        val model = AnimeDownloadQueueScreenModel(manager)

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
        val model = AnimeDownloadQueueScreenModel(manager)

        model.reorderQueue(selector = { it.download.episode.id })
        advanceUntilIdle()

        val slot = slot<List<Long>>()
        coVerify { manager.reorderQueueByEpisodeIds(capture(slot)) }
        slot.captured shouldContainExactly listOf(1L, 2L, 3L)
    }

    @Test
    fun `reorderQueue with reverse=true sorts in descending order`() = runTest {
        val source = createSource(1L)
        val d1 = createDownload(source, 1L, 1L)
        val d2 = createDownload(source, 1L, 2L)
        val d3 = createDownload(source, 1L, 3L)
        val manager = createManager(listOf(d1, d2, d3))
        val model = AnimeDownloadQueueScreenModel(manager)

        model.reorderQueue(selector = { it.download.episode.id }, reverse = true)
        advanceUntilIdle()

        val slot = slot<List<Long>>()
        coVerify { manager.reorderQueueByEpisodeIds(capture(slot)) }
        slot.captured shouldContainExactly listOf(3L, 2L, 1L)
    }
}
