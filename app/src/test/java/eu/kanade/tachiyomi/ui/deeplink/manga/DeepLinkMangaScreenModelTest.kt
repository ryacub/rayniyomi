package eu.kanade.tachiyomi.ui.deeplink.manga

import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.interactor.NetworkToLocalManga
import tachiyomi.domain.items.chapter.interactor.GetChapterByUrlAndMangaId
import tachiyomi.domain.source.manga.service.MangaSourceManager
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DeepLinkMangaScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `parse failure updates state to Error with the same Throwable`() = runTest {
        val failure = IllegalArgumentException("parse failed")
        val model = createModel(failure)

        val state = awaitError(model)

        assertSame(failure, state.error)
    }

    @Test
    fun `network IOException updates state to Error with the same Throwable`() = runTest {
        val failure = IOException("network failed")
        val model = createModel(failure)

        val state = awaitError(model)

        assertSame(failure, state.error)
    }

    @Test
    fun `LinkageError updates state to Error with the same Throwable`() = runTest {
        val failure = LinkageError("source linkage failed")
        val model = createModel(failure)

        val state = awaitError(model)

        assertSame(failure, state.error)
    }

    @Test
    fun `CancellationException does not update state to Error`() = runTest {
        val failure = CancellationException("cancelled")
        val invocationComplete = CompletableDeferred<Unit>()
        val sourceManager = mockk<MangaSourceManager> {
            every { getAll() } answers {
                invocationComplete.complete(Unit)
                throw failure
            }
        }
        val model = createModel(sourceManager)

        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                invocationComplete.await()
            }
        }

        val errorState = try {
            withContext(Dispatchers.Default.limitedParallelism(1)) {
                withTimeout(1_000) {
                    model.state.first { it is DeepLinkMangaScreenModel.State.Error }
                }
            }
            null
        } catch (error: Throwable) {
            error
        }

        assertInstanceOf(TimeoutCancellationException::class.java, errorState)
        assertInstanceOf(DeepLinkMangaScreenModel.State.Loading::class.java, model.state.value)
        assertFalse(model.state.value is DeepLinkMangaScreenModel.State.Error)
    }

    private suspend fun awaitError(
        model: DeepLinkMangaScreenModel,
    ): DeepLinkMangaScreenModel.State.Error {
        return withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                model.state.first { it is DeepLinkMangaScreenModel.State.Error }
                    as DeepLinkMangaScreenModel.State.Error
            }
        }
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
        )
    }
}
