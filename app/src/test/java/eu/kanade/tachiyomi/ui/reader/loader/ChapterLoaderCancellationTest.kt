package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ChapterLoaderCancellationTest {

    private val previousGraph = AppGraphHolder.graphOrNull
    private val chapter = ReaderChapter(Chapter.create().copy(id = 2L, url = "chapter-1"))

    init {
        AppGraphHolder.graph = mockk<AppGraph>(relaxed = true) {
            every { chapterCache } returns mockk<ChapterCache> {
                every { getPageListFromCache(any()) } throws IOException("not cached")
            }
            every { sourcePreferences } returns SourcePreferences(InMemoryPreferenceStore())
        }
    }

    @AfterEach
    fun tearDown() {
        chapter.pageLoader?.recycle()
        AppGraphHolder.graphOrNull = previousGraph
    }

    private fun loaderFor(source: HttpSource) = ChapterLoader(
        context = mockk(relaxed = true),
        downloadManager = mockk<MangaDownloadManager> {
            every { isChapterDownloaded(any(), any(), any(), any(), any()) } returns false
        },
        downloadProvider = mockk(relaxed = true),
        manga = Manga.create().copy(id = 1L, source = 1L),
        source = source,
    )

    @Test
    fun `a cancelled load returns the chapter to Wait instead of Error`() = runBlocking<Unit> {
        val pageListRequested = CompletableDeferred<Unit>()
        val source = mockk<HttpSource>(relaxed = true) {
            coEvery { getPageList(any()) } coAnswers {
                pageListRequested.complete(Unit)
                awaitCancellation()
            }
        }
        val loader = loaderFor(source)

        val load = launch { loader.loadChapter(chapter) }
        withTimeout(5_000) { pageListRequested.await() }
        load.cancelAndJoin()

        chapter.state shouldBe ReaderChapter.State.Wait
    }

    @Test
    fun `a load cancelled before it reaches the page loader leaves the chapter in Wait`() = runBlocking<Unit> {
        val loader = loaderFor(mockk<HttpSource>(relaxed = true))

        val load = launch(start = CoroutineStart.UNDISPATCHED) {
            coroutineContext.job.cancel()
            loader.loadChapter(chapter)
        }
        load.join()

        chapter.state shouldBe ReaderChapter.State.Wait
    }

    @Test
    fun `a failed load still records the error`() = runBlocking<Unit> {
        val source = mockk<HttpSource>(relaxed = true) {
            coEvery { getPageList(any()) } throws IOException("offline")
        }
        val loader = loaderFor(source)

        shouldThrow<IOException> { loader.loadChapter(chapter) }

        chapter.state.shouldBeInstanceOf<ReaderChapter.State.Error>().error.message shouldBe "offline"
    }

    @Test
    fun `two concurrent loads of one chapter start one page-list request`() = runBlocking<Unit> {
        val gated = GatedPageList()
        val loader = loaderFor(gated.source)

        val first = async { loader.loadChapter(chapter) }
        gated.awaitCalls(1)
        val second = async { loader.loadChapter(chapter) }
        gated.awaitCallsOrNull(2)
        gated.open()
        withTimeout(5_000) {
            first.await()
            second.await()
        }

        gated.calls shouldBe 1
        chapter.state.shouldBeInstanceOf<ReaderChapter.State.Loaded>()
    }

    @Test
    fun `a cancelled caller leaves the shared load running`() = runBlocking<Unit> {
        val gated = GatedPageList()
        val loader = loaderFor(gated.source)

        val first = launch { loader.loadChapter(chapter) }
        gated.awaitCalls(1)
        val second = async { loader.loadChapter(chapter) }
        gated.awaitCallsOrNull(2)
        first.cancelAndJoin()

        chapter.state shouldBe ReaderChapter.State.Loading
        gated.open()
        withTimeout(5_000) { second.await() }
        chapter.state.shouldBeInstanceOf<ReaderChapter.State.Loaded>()
    }

    /** A source whose page list waits for [open], and that counts its requests. */
    private class GatedPageList {
        private val gate = CompletableDeferred<Unit>()
        private val requests = AtomicInteger()
        val calls: Int get() = requests.get()

        val source = mockk<HttpSource>(relaxed = true) {
            coEvery { getPageList(any()) } coAnswers {
                requests.incrementAndGet()
                gate.await()
                listOf(Page(index = 0, url = "page-0", imageUrl = "image-0"))
            }
        }

        fun open() = gate.complete(Unit)

        suspend fun awaitCalls(count: Int) = withTimeout(5_000) { waitFor(count) }

        /** Gives a second request time to start. A single owner never sends it. */
        suspend fun awaitCallsOrNull(count: Int) = withTimeoutOrNull(500) { waitFor(count) }

        private suspend fun waitFor(count: Int) {
            while (requests.get() < count) yield()
        }
    }
}
