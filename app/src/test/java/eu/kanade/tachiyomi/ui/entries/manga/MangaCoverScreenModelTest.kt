package eu.kanade.tachiyomi.ui.entries.manga

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
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga

class MangaCoverScreenModelTest {

    @Test
    fun `clearing the store stops the manga subscription`() {
        runBlocking {
            val mangaFlow = MutableSharedFlow<Manga>()
            val getManga = mockk<GetManga> { coEvery { subscribe(1L) } returns mangaFlow }
            val store = ViewModelStore()
            store.create {
                MangaCoverScreenModel(
                    mangaId = 1L,
                    getManga = getManga,
                    imageSaver = mockk(relaxed = true),
                    coverCache = mockk(relaxed = true),
                    updateManga = mockk(relaxed = true),
                )
            }
            awaitSubscribers(mangaFlow, expected = 1)

            store.clear()

            awaitSubscribers(mangaFlow, expected = 0)
            mangaFlow.subscriptionCount.value shouldBe 0
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
