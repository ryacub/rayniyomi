package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import eu.kanade.domain.source.manga.model.RemoteMangaUpdate
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.source.MangaSource
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

@OptIn(ExperimentalCoroutinesApi::class)
class MangaEntryRefresherTest {

    private val source = mockk<MangaSource>()
    private val manga = Manga.create().copy(id = 1L, source = 1L, favorite = true)
    private val newChapter = Chapter.create().copy(id = 10L, mangaId = 1L)
    private val updateMangaFromRemote = mockk<UpdateMangaFromRemote>()
    private val filterChaptersForDownload = mockk<FilterChaptersForDownload>()
    private val downloadManager = mockk<MangaDownloadManager>(relaxed = true)

    @Test
    fun `a refresh queues the new chapters that the download filter keeps`() = runTest {
        sourceReturns(listOf(newChapter))
        coEvery { filterChaptersForDownload.await(manga, listOf(newChapter)) } returns listOf(newChapter)

        val result = refresher().refresh(source, manga).await()

        result.isSuccess shouldBe true
        coVerify { updateMangaFromRemote(source, manga, true, true, true, any()) }
        verify { downloadManager.downloadChapters(manga, listOf(newChapter)) }
    }

    @Test
    fun `a refresh queues nothing when the download filter keeps no chapter`() = runTest {
        sourceReturns(listOf(newChapter))
        coEvery { filterChaptersForDownload.await(any(), any()) } returns emptyList()

        refresher().refresh(source, manga).await()

        verify(exactly = 0) { downloadManager.downloadChapters(any(), any(), any()) }
    }

    @Test
    fun `a failed refresh returns the error and queues nothing`() = runTest {
        val error = IllegalStateException("source down")
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } returns Result.failure(error)

        val result = refresher().refresh(source, manga).await()

        result.exceptionOrNull() shouldBe error
        verify(exactly = 0) { downloadManager.downloadChapters(any(), any(), any()) }
    }

    @Test
    fun `a refresh runs to the end after its caller is cancelled`() = runTest {
        val gate = CompletableDeferred<Unit>()
        sourceReturns(listOf(newChapter), gate)
        coEvery { filterChaptersForDownload.await(any(), any()) } returns listOf(newChapter)
        val refresher = refresher()

        val caller = launch { refresher.refresh(source, manga).await() }
        advanceUntilIdle()
        caller.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        verify { downloadManager.downloadChapters(manga, listOf(newChapter)) }
    }

    @Test
    fun `a second refresh of the same entry joins the running one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        sourceReturns(emptyList(), gate)
        val refresher = refresher()

        val first = refresher.refresh(source, manga)
        val second = refresher.refresh(source, manga)
        gate.complete(Unit)
        advanceUntilIdle()

        (first === second) shouldBe true
        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a refresh after the previous one finished fetches again`() = runTest {
        sourceReturns(emptyList())
        val refresher = refresher()

        refresher.refresh(source, manga).await()
        refresher.refresh(source, manga).await()

        coVerify(exactly = 2) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
    }

    private fun sourceReturns(newChapters: List<Chapter>, gate: CompletableDeferred<Unit>? = null) {
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } coAnswers {
            gate?.await()
            Result.success(RemoteMangaUpdate(manga = manga, newChapters = newChapters))
        }
        coEvery { filterChaptersForDownload.await(any(), emptyList()) } returns emptyList()
    }

    private fun TestScope.refresher(
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
    ) = MangaEntryRefresher(
        updateMangaFromRemote = updateMangaFromRemote,
        filterChaptersForDownload = filterChaptersForDownload,
        downloadManager = downloadManager,
        dispatcher = dispatcher,
    )
}
