package eu.kanade.tachiyomi.extension.anime

import androidx.core.content.ContextCompat
import eu.kanade.domain.extension.anime.interactor.TrustAnimeExtension
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.coroutines.ContinuationInterceptor

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

    @Test
    fun `scope carries the injected dispatcher`() = runTest {
        val events = mutableListOf<String>()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scopeDispatcher = StandardTestDispatcher(testScheduler)

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

        val manager = AnimeExtensionManager(
            context = mockk(relaxed = true),
            preferences = SourcePreferences(InMemoryPreferenceStore()),
            trustExtension = mockk(relaxed = true),
            scopeDispatcher = scopeDispatcher,
            ioDispatcher = dispatcher,
        )

        manager.scope.coroutineContext[ContinuationInterceptor] shouldBe scopeDispatcher
    }

    @Test
    fun `trust loads the extension on the injected IO dispatcher`() = runTest {
        val events = mutableListOf<String>()
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val callerDispatcher = StandardTestDispatcher(testScheduler)
        val untrusted = untrusted(PKG)
        val installed = installed(PKG)
        var loaderDispatcher: ContinuationInterceptor? = null

        val manager = createManager(
            events = events,
            ioDispatcher = ioDispatcher,
            initialResults = listOf(AnimeLoadResult.Untrusted(untrusted)),
        )
        coEvery { AnimeExtensionLoader.loadExtensionFromPkgName(any(), PKG) } coAnswers {
            loaderDispatcher = currentCoroutineContext()[ContinuationInterceptor]
            AnimeLoadResult.Success(installed)
        }
        advanceUntilIdle()

        withContext(callerDispatcher) {
            manager.trust(untrusted)
        }
        advanceUntilIdle()

        loaderDispatcher shouldBe ioDispatcher
        manager.installedExtensionsFlow.first { it == listOf(installed) } shouldBe listOf(installed)
        manager.scope.cancel()
    }

    private fun createManager(
        events: MutableList<String>,
        ioDispatcher: CoroutineDispatcher,
        initialResults: List<AnimeLoadResult> = emptyList(),
    ): AnimeExtensionManager {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), any(), any(), any<Int>()) } answers {
            events += "registered"
            mockk(relaxed = true)
        }
        mockkObject(AnimeExtensionLoader)
        coEvery { AnimeExtensionLoader.loadExtensions(any()) } coAnswers {
            events += "scan-started"
            initialResults
        }

        return AnimeExtensionManager(
            context = mockk(relaxed = true),
            preferences = SourcePreferences(InMemoryPreferenceStore()),
            trustExtension = mockk(relaxed = true),
            ioDispatcher = ioDispatcher,
        )
    }

    private fun untrusted(pkgName: String) = AnimeExtension.Untrusted(
        name = pkgName,
        pkgName = pkgName,
        versionName = "14.1",
        versionCode = 1,
        libVersion = 14.0,
        signatureHash = "hash",
    )

    private fun installed(pkgName: String) = AnimeExtension.Installed(
        name = pkgName,
        pkgName = pkgName,
        versionName = "14.1",
        versionCode = 1,
        libVersion = 14.0,
        lang = "en",
        isNsfw = false,
        pkgFactory = null,
        sources = listOf(mockk<AnimeSource>()),
        icon = null,
        isShared = true,
        signatureHash = "hash",
    )

    private companion object {
        const val PKG = "com.example.anime.trusted"
    }
}
