package eu.kanade.tachiyomi.ui.entries.anime

import androidx.lifecycle.ViewModelStore
import eu.kanade.tachiyomi.test.create
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.anime.model.Anime

class AnimeImageScreenModelTest {

    @Test
    fun `clearing the store stops the anime subscription`() {
        runBlocking {
            val animeFlow = MutableSharedFlow<Anime>()
            val getAnime = mockk<GetAnime> { coEvery { subscribe(1L) } returns animeFlow }
            val store = ViewModelStore()
            store.create {
                AnimeImageScreenModel(
                    animeId = 1L,
                    getAnime = getAnime,
                    imageSaver = mockk(relaxed = true),
                    coverCache = mockk(relaxed = true),
                    backgroundCache = mockk(relaxed = true),
                    updateAnime = mockk(relaxed = true),
                    pagerState = mockk(relaxed = true),
                )
            }
            awaitSubscribers(animeFlow, expected = 1)

            store.clear()

            awaitSubscribers(animeFlow, expected = 0)
            animeFlow.subscriptionCount.value shouldBe 0
        }
    }

    private suspend fun awaitSubscribers(flow: MutableSharedFlow<*>, expected: Int) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (flow.subscriptionCount.value != expected) delay(10)
            }
        }
    }
}
