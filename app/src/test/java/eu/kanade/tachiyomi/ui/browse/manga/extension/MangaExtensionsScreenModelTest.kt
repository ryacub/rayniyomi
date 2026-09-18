package eu.kanade.tachiyomi.ui.browse.manga.extension

import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.extension.manga.model.MangaExtensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
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
class MangaExtensionsScreenModelTest {

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
        val extension = availableExtension()
        val extensionManager = mockk<MangaExtensionManager>(relaxed = true)
        val errorEmitted = CompletableDeferred<Unit>()
        val allowCompletion = CompletableDeferred<Unit>()
        every { extensionManager.installExtension(extension) } returns flow {
            emit(InstallStep.Error)
            errorEmitted.complete(Unit)
            allowCompletion.await()
        }
        val getExtensions = mockk<GetMangaExtensionsByType> {
            every { subscribe() } returns flowOf(
                MangaExtensions(
                    updates = emptyList(),
                    installed = emptyList(),
                    available = listOf(extension),
                    untrusted = emptyList(),
                ),
            )
        }
        val model = createModel(
            extensionManager,
            getExtensions,
            StandardTestDispatcher(testScheduler),
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

    @Test
    fun `retry routes a failed installed extension to update`() = runTest {
        val extensionManager = mockk<MangaExtensionManager>(relaxed = true)
        val getExtensions = mockk<GetMangaExtensionsByType> {
            every { subscribe() } returns emptyFlow()
        }
        val model = createModel(
            extensionManager,
            getExtensions,
            StandardTestDispatcher(testScheduler),
        )
        val extension = installedExtension()

        model.retryInstallUpdateExtension(extension)
        advanceUntilIdle()

        verify(exactly = 1) { extensionManager.updateExtension(extension) }
    }

    private fun createModel(
        extensionManager: MangaExtensionManager,
        getExtensions: GetMangaExtensionsByType,
        installDispatcher: CoroutineDispatcher,
    ) = MangaExtensionsScreenModel(
        preferences = SourcePreferences(InMemoryPreferenceStore()),
        basePreferences = BasePreferences(mockk(relaxed = true), InMemoryPreferenceStore()),
        extensionManager = extensionManager,
        getExtensions = getExtensions,
        application = mockk(relaxed = true),
        installDispatcher = installDispatcher,
    )

    private fun availableExtension() = MangaExtension.Available(
        name = "Extension",
        pkgName = "manga.extension",
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

    private fun installedExtension() = MangaExtension.Installed(
        name = "Extension",
        pkgName = "manga.extension",
        versionName = "1.0",
        versionCode = 1,
        libVersion = 1.0,
        lang = "en",
        isNsfw = false,
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        hasUpdate = true,
        isShared = false,
        signatureHash = "signature",
    )
}
