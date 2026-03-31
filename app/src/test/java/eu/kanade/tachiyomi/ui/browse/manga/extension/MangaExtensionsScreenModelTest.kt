package eu.kanade.tachiyomi.ui.browse.manga.extension

import android.app.Application
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.base.ExtensionInstallerPreference
import eu.kanade.domain.extension.manga.interactor.GetMangaExtensionsByType
import eu.kanade.domain.extension.manga.model.MangaExtensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.manga.MangaExtensionManager
import eu.kanade.tachiyomi.extension.manga.model.InvalidReason
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

@OptIn(ExperimentalCoroutinesApi::class)
class MangaExtensionsScreenModelTest {

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
    fun `invalid extension notice is emitted once and late collector receives it`() = runTest {
        val invalid = MangaLoadResult.Invalid(
            pkgName = "com.example.bad",
            name = "Broken Extension",
            versionName = "1.0.0",
            versionCode = 1L,
            signatureHash = "hash-z",
            reason = InvalidReason.SOURCE_ID_THROW,
        )
        val invalidNotices = MutableSharedFlow<MangaLoadResult.Invalid>(replay = 2)
        invalidNotices.tryEmit(invalid)
        invalidNotices.tryEmit(invalid)
        val model = createModel(invalidNotices)

        advanceUntilIdle()

        val event = model.events.first()
        assertEquals(MangaExtensionsScreenModel.Event.InvalidExtensionRevoked(invalid), event)

        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking {
                withTimeout(250) {
                    model.events.first()
                }
            }
        }
    }

    private fun createModel(
        invalidNotices: SharedFlow<MangaLoadResult.Invalid>,
    ): MangaExtensionsScreenModel {
        val preferences = SourcePreferences(InMemoryPreferenceStore())
        val basePreferences = mockk<BasePreferences>()
        val installerPreference = mockk<ExtensionInstallerPreference>()
        every { basePreferences.extensionInstaller() } returns installerPreference
        every { installerPreference.changes() } returns emptyFlow()

        val extensionManager = mockk<MangaExtensionManager>()
        every { extensionManager.invalidExtensionNotices } returns invalidNotices
        coEvery { extensionManager.findAvailableExtensions() } returns Unit

        val getExtensions = mockk<GetMangaExtensionsByType>()
        every { getExtensions.subscribe() } returns flowOf(
            MangaExtensions(
                updates = emptyList(),
                installed = emptyList(),
                available = emptyList(),
                untrusted = emptyList(),
            ),
        )

        return MangaExtensionsScreenModel(
            preferences = preferences,
            basePreferences = basePreferences,
            extensionManager = extensionManager,
            getExtensions = getExtensions,
            application = mockk<Application>(relaxed = true),
        )
    }
}
