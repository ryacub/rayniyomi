package eu.kanade.tachiyomi.ui.deeplink.anime

import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.GetAnimeByUrlAndSourceId
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.items.episode.interactor.GetEpisodeByUrlAndAnimeId
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DeepLinkAnimeScreenModelTest {

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
    fun `parse failures become Error with the same Throwable`() = runTest {
        val failure = IllegalArgumentException("parse failed")
        val model = createModel(failure)

        val state = awaitError(model)

        assertSame(failure, state.error)
    }

    @Test
    fun `network failures become Error with the same Throwable`() = runTest {
        val failure = IOException("network failed")
        val model = createModel(failure)

        val state = awaitError(model)

        assertSame(failure, state.error)
    }

    @Test
    fun `linkage failures become Error with the same Throwable`() = runTest {
        val failure = LinkageError("extension linkage failed")
        val model = createModel(failure)

        val state = awaitError(model)

        assertSame(failure, state.error)
    }

    @Test
    fun `cancellation failures do not become Error`() = runTest {
        val invoked = CompletableDeferred<Unit>()
        val model = createModel(
            failure = CancellationException("resolution cancelled"),
            invoked = invoked,
        )

        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { invoked.await() }
        }

        val errorState = withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeoutOrNull(1_000) {
                model.state.first { it is DeepLinkAnimeScreenModel.State.Error }
            }
        }

        assertNull(errorState)
        assertEquals(DeepLinkAnimeScreenModel.State.Loading, model.state.value)
    }

    private suspend fun awaitError(model: DeepLinkAnimeScreenModel): DeepLinkAnimeScreenModel.State.Error {
        return withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                model.state.first { it is DeepLinkAnimeScreenModel.State.Error }
                    as DeepLinkAnimeScreenModel.State.Error
            }
        }
    }

    private fun createModel(
        failure: Throwable? = null,
        invoked: CompletableDeferred<Unit>? = null,
    ): DeepLinkAnimeScreenModel {
        return DeepLinkAnimeScreenModel(
            query = "https://example.com/anime/1",
            sourceManager = createSourceManager(failure, invoked),
            networkToLocalAnime = mockk<NetworkToLocalAnime>(),
            getEpisodeByUrlAndAnimeId = mockk<GetEpisodeByUrlAndAnimeId>(),
            getAnimeByUrlAndSourceId = mockk<GetAnimeByUrlAndSourceId>(),
            syncEpisodesWithSource = mockk<SyncEpisodesWithSource>(),
        )
    }

    private fun createSourceManager(
        failure: Throwable?,
        invoked: CompletableDeferred<Unit>?,
    ): AnimeSourceManager {
        return object : AnimeSourceManager {
            override val isInitialized: StateFlow<Boolean> = MutableStateFlow(true)
            override val catalogueSources: Flow<List<AnimeCatalogueSource>> = emptyFlow()

            override fun get(sourceKey: Long): AnimeSource? = null

            override fun getOrStub(sourceKey: Long): AnimeSource {
                error("Not used in this test")
            }

            override fun getOnlineSources(): List<AnimeHttpSource> = emptyList()

            override fun getCatalogueSources(): List<AnimeCatalogueSource> {
                invoked?.complete(Unit)
                if (failure != null) throw failure
                return emptyList()
            }

            override fun getStubSources(): List<StubAnimeSource> = emptyList()
        }
    }
}
