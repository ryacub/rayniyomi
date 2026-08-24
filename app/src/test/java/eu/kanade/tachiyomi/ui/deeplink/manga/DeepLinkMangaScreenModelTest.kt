package eu.kanade.tachiyomi.ui.deeplink.manga

import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.test.awaitAssert
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.items.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.source.manga.service.MangaSourceManager
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DeepLinkMangaScreenModelTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `parse failure updates state to Error with the same Throwable`() = runTest(vt.scheduler) {
        val failure = IllegalArgumentException("parse failed")
        val model = createModel(failure)

        awaitAssert({ model.state.value }) { it is DeepLinkMangaScreenModel.State.Error }
        val state = model.state.value as DeepLinkMangaScreenModel.State.Error

        assertSame(failure, state.error)
    }

    @Test
    fun `network IOException updates state to Error with the same Throwable`() = runTest(vt.scheduler) {
        val failure = IOException("network failed")
        val model = createModel(failure)

        awaitAssert({ model.state.value }) { it is DeepLinkMangaScreenModel.State.Error }
        val state = model.state.value as DeepLinkMangaScreenModel.State.Error

        assertSame(failure, state.error)
    }

    @Test
    fun `LinkageError updates state to Error with the same Throwable`() = runTest(vt.scheduler) {
        val failure = LinkageError("source linkage failed")
        val model = createModel(failure)

        awaitAssert({ model.state.value }) { it is DeepLinkMangaScreenModel.State.Error }
        val state = model.state.value as DeepLinkMangaScreenModel.State.Error

        assertSame(failure, state.error)
    }

    @Test
    fun `CancellationException does not update state to Error`() = runTest(vt.scheduler) {
        val failure = CancellationException("cancelled")
        val invocationComplete = CompletableDeferred<Unit>()
        val sourceManager = mockk<MangaSourceManager> {
            every { getAll() } answers {
                invocationComplete.complete(Unit)
                throw failure
            }
        }
        val model = createModel(sourceManager)

        advanceUntilIdle()

        assertTrue(invocationComplete.isCompleted)
        assertEquals(DeepLinkMangaScreenModel.State.Loading, model.state.value)
        assertFalse(model.state.value is DeepLinkMangaScreenModel.State.Error)
    }

    private fun createModel(failure: Throwable): DeepLinkMangaScreenModel {
        val sourceManager = mockk<MangaSourceManager> {
            every { getAll() } throws failure
        }
        return createModel(sourceManager)
    }

    private fun createModel(sourceManager: MangaSourceManager): DeepLinkMangaScreenModel {
        return DeepLinkMangaScreenModel(
            query = "https://example.com/manga/1",
            sourceManager = sourceManager,
            networkToLocalManga = mockk<NetworkToLocalManga>(),
            getChapterByUrlAndMangaId = mockk<GetChapterByUrlAndMangaId>(),
            getMangaByUrlAndSourceId = mockk<GetMangaByUrlAndSourceId>(),
            updateMangaFromRemote = mockk<UpdateMangaFromRemote>(),
            ioDispatcher = vt.io,
        )
    }
}
