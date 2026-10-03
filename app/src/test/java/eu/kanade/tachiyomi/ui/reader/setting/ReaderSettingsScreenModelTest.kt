package eu.kanade.tachiyomi.ui.reader.setting

import androidx.lifecycle.ViewModelStore
import eu.kanade.tachiyomi.test.create
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test

class ReaderSettingsScreenModelTest {

    @Test
    fun `clearing the store stops the reader state subscription`() {
        runBlocking {
            val readerState = MutableStateFlow(ReaderViewModel.State())
            val store = ViewModelStore()
            val model = store.create {
                ReaderSettingsScreenModel(
                    readerState = readerState,
                    hasDisplayCutout = false,
                    onChangeReadingMode = {},
                    onChangeOrientation = {},
                    preferences = mockk(relaxed = true),
                )
            }

            // The Lazily share starts on the first subscriber and then runs on the IO dispatcher.
            model.viewerFlow.first()
            awaitSubscribers(readerState, expected = 1)

            store.clear()

            awaitSubscribers(readerState, expected = 0)
            readerState.subscriptionCount.value shouldBe 0
        }
    }

    private suspend fun awaitSubscribers(flow: MutableStateFlow<*>, expected: Int) {
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                while (flow.subscriptionCount.value != expected) delay(10)
            }
        }
    }
}
