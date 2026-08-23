package eu.kanade.tachiyomi.extension.anime

import androidx.core.content.ContextCompat
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.util.AnimeExtensionLoader
import eu.kanade.tachiyomi.ui.updates.InMemoryPreferenceStore
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class AnimeExtensionManagerInitTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `install receiver registration is deferred off the constructing thread`() = runTest {
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        val manager = createManager(events, dispatcher)

        verify(exactly = 0) { ContextCompat.registerReceiver(any(), any(), any(), any<Int>()) }
        advanceUntilIdle()
        verify(exactly = 1) { ContextCompat.registerReceiver(any(), any(), any(), any<Int>()) }
        manager.isInitialized.value shouldBe true
    }

    @Test
    fun `registration precedes the initial extension scan`() = runTest {
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        createManager(events, dispatcher)
        advanceUntilIdle()

        events shouldBe listOf("registered", "scan-started")
        coVerify(exactly = 1) { AnimeExtensionLoader.loadExtensions(any()) }
    }

    @Test
    fun `initial scan still runs when receiver registration fails`() = runTest {
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)

        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any<Int>()) } throws
            RuntimeException("registration failed")
        mockkObject(AnimeExtensionLoader)
        coEvery { AnimeExtensionLoader.loadExtensions(any()) } answers {
            events += "scan-started"
            emptyList()
        }

        val manager = AnimeExtensionManager(
            context = mockk(relaxed = true),
            preferences = SourcePreferences(InMemoryPreferenceStore()),
            trustExtension = mockk(relaxed = true),
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()

        coVerify(exactly = 1) { AnimeExtensionLoader.loadExtensions(any()) }
        manager.isInitialized.value shouldBe true
    }

    private fun createManager(
        events: MutableList<String>,
        ioDispatcher: CoroutineDispatcher,
    ): AnimeExtensionManager {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any<Int>()) } answers {
            events += "registered"
            mockk(relaxed = true)
        }
        mockkObject(AnimeExtensionLoader)
        coEvery { AnimeExtensionLoader.loadExtensions(any()) } answers {
            events += "scan-started"
            emptyList()
        }

        return AnimeExtensionManager(
            context = mockk(relaxed = true),
            preferences = SourcePreferences(InMemoryPreferenceStore()),
            trustExtension = mockk(relaxed = true),
            ioDispatcher = ioDispatcher,
        )
    }
}
