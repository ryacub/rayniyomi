package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import java.io.IOException

class ChapterLoaderReloadTest {

    private val chapter = ReaderChapter(Chapter.create().copy(id = 1L, mangaId = 1L, name = "Chapter 1"))

    /**
     * Every chapter here has a page loader, so the owner never opens a new one. The owner's
     * supervisor job stays active until its scope ends, so it gets its own scope on the test
     * scheduler instead of a child of the test.
     */
    private fun TestScope.owner() = ChapterLoader(
        context = mockk(relaxed = true),
        downloadManager = mockk(relaxed = true),
        downloadProvider = mockk(relaxed = true),
        manga = Manga.create(),
        source = mockk(relaxed = true),
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    private class ScriptedLoader(private val responses: List<suspend () -> List<ReaderPage>>) : PageLoader() {
        var calls = 0

        override var isLocal: Boolean = true

        override suspend fun getPages(): List<ReaderPage> = responses[calls++]()
    }

    private fun pages(vararg indexes: Int) = indexes.map { ReaderPage(it) }

    @Test
    fun `a reload asks the page loader again and installs the new pages`() = runTest {
        val fresh = pages(0, 1)
        chapter.state = ReaderChapter.State.Loaded(pages(0, 1))
        val loader = ScriptedLoader(listOf({ fresh }))
        chapter.pageLoader = loader

        owner().reloadChapter(chapter)

        loader.calls shouldBe 1
        chapter.pages.shouldBeSameInstanceAs(fresh)
        fresh.forEach { it.chapter shouldBe chapter }
    }

    @Test
    fun `a waiting or failed chapter is reloaded into a loaded state`() = runTest {
        listOf(ReaderChapter.State.Wait, ReaderChapter.State.Error(IOException("offline"))).forEach { start ->
            val fresh = pages(0)
            chapter.state = start
            chapter.pageLoader = ScriptedLoader(listOf({ fresh }))

            owner().reloadChapter(chapter)

            chapter.pages.shouldBeSameInstanceAs(fresh)
        }
    }

    @Test
    fun `a failed reload of a loaded chapter keeps its pages and throws`() = runTest {
        val old = pages(0)
        chapter.state = ReaderChapter.State.Loaded(old)
        chapter.pageLoader = ScriptedLoader(listOf({ throw IOException("loader broke") }))

        shouldThrow<IOException> { owner().reloadChapter(chapter) }

        chapter.pages.shouldBeSameInstanceAs(old)
    }

    @Test
    fun `a cancelled reload of a loaded chapter keeps its pages`() = runTest {
        val old = pages(0)
        chapter.state = ReaderChapter.State.Loaded(old)
        chapter.pageLoader = ScriptedLoader(listOf({ throw CancellationException("reload cancelled") }))

        shouldThrow<CancellationException> { owner().reloadChapter(chapter) }

        chapter.pages.shouldBeSameInstanceAs(old)
    }

    @Test
    fun `a replaced attempt never writes over the newer attempt`() = runTest {
        val stale = pages(0)
        val fresh = pages(1)
        val staleGate = CompletableDeferred<Unit>()
        val freshGate = CompletableDeferred<Unit>()
        chapter.state = ReaderChapter.State.Wait
        chapter.pageLoader = ScriptedLoader(
            listOf(
                // The first attempt ignores cancellation, so its result lands after the reload.
                {
                    withContext(NonCancellable) { staleGate.await() }
                    stale
                },
                {
                    freshGate.await()
                    fresh
                },
            ),
        )
        val owner = owner()
        val load = async { owner.reloadChapter(chapter) }
        advanceUntilIdle()

        val reload = launch { owner.reloadChapter(chapter) }
        advanceUntilIdle()
        staleGate.complete(Unit)
        advanceUntilIdle()

        chapter.state shouldBe ReaderChapter.State.Loading
        freshGate.complete(Unit)
        advanceUntilIdle()
        reload.join()
        chapter.pages.shouldBeSameInstanceAs(fresh)
        load.isCompleted shouldBe true
        load.isCancelled shouldBe false
    }

    @Test
    fun `a replaced attempt that fails never writes an error over the newer attempt`() = runTest {
        val fresh = pages(1)
        val staleGate = CompletableDeferred<Unit>()
        val freshGate = CompletableDeferred<Unit>()
        chapter.state = ReaderChapter.State.Wait
        chapter.pageLoader = ScriptedLoader(
            listOf(
                // A blocking read can report its cancellation as an IOException.
                {
                    withContext(NonCancellable) { staleGate.await() }
                    throw IOException("stream closed")
                },
                {
                    freshGate.await()
                    fresh
                },
            ),
        )
        val owner = owner()
        launch { owner.reloadChapter(chapter) }
        advanceUntilIdle()

        val reload = launch { owner.reloadChapter(chapter) }
        advanceUntilIdle()
        staleGate.complete(Unit)
        advanceUntilIdle()

        chapter.state shouldBe ReaderChapter.State.Loading
        freshGate.complete(Unit)
        reload.join()
        chapter.pages.shouldBeSameInstanceAs(fresh)
    }

    @Test
    fun `a chapter released during a reload keeps no recycled page loader`() = runTest {
        val gate = CompletableDeferred<Unit>()
        chapter.state = ReaderChapter.State.Loaded(pages(0))
        val loader = ScriptedLoader(
            listOf({
                gate.await()
                pages(1)
            }),
        )
        chapter.pageLoader = loader
        val owner = owner()
        val reload = async { owner.reloadChapter(chapter) }
        advanceUntilIdle()

        chapter.ref()
        chapter.unref()
        gate.complete(Unit)
        advanceUntilIdle()

        reload.isCancelled shouldBe true
        loader.isRecycled shouldBe true
        chapter.pageLoader shouldBe null
        chapter.state.shouldBeInstanceOf<ReaderChapter.State.Wait>()
    }
}
