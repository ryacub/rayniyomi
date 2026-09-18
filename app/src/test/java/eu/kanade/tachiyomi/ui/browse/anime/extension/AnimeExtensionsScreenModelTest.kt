package eu.kanade.tachiyomi.ui.browse.anime.extension

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.extension.anime.model.AnimeExtensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeExtensionsScreenModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `installer error remains after the flow completes`() = runTest {
        val extension = AnimeExtension.Available(
            name = "Extension",
            pkgName = "anime.extension",
            versionName = "1.0",
            versionCode = 1,
            libVersion = 1.0,
            lang = "en",
            isNsfw = false,
            sources = emptyList(),
            apkName = "extension.apk",
            iconUrl = "",
            repoUrl = "https://example.test/repo",
            signingKeyFingerprint = "signature",
        )
        val extensionManager = mockk<AnimeExtensionManager>(relaxed = true)
        val errorEmitted = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()
        every { extensionManager.installExtension(extension) } returns flow {
            emit(InstallStep.Error)
            errorEmitted.complete(Unit)
            allowCompletion.await()
        }
        val getExtensions = mockk<GetAnimeExtensionsByType> {
            every { subscribe() } returns flowOf(
                AnimeExtensions(
                    updates = emptyList(),
                    installed = emptyList(),
                    available = listOf(extension),
                    untrusted = emptyList(),
                ),
            )
        }
        val model = AnimeExtensionsScreenModel(
            preferences = SourcePreferences(InMemoryPreferenceStore()),
            basePreferences = BasePreferences(mockk(relaxed = true), InMemoryPreferenceStore()),
            extensionManager = extensionManager,
            getExtensions = getExtensions,
            application = mockk(relaxed = true),
            installDispatcher = StandardTestDispatcher(testScheduler),
        )
        advanceUntilIdle()

        model.installExtension(extension)
        errorEmitted.await()
        model.installExtension(extension)
        verify(exactly = 1) { extensionManager.installExtension(extension) }
        allowCompletion.complete(Unit)
        advanceUntilIdle()

        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                model.state.first { state ->
                    state.items.values.flatten()
                        .firstOrNull { it.extension.pkgName == extension.pkgName }
                        ?.installStep == InstallStep.Error
                }
            }
        }
    }
}
