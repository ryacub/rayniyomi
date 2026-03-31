package eu.kanade.tachiyomi.ui.browse.anime.extension

import android.app.Application
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.base.ExtensionInstallerPreference
import eu.kanade.domain.extension.anime.interactor.GetAnimeExtensionsByType
import eu.kanade.domain.extension.anime.model.AnimeExtensions
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import eu.kanade.tachiyomi.extension.anime.model.InvalidReason
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.system.NetworkState
import eu.kanade.tachiyomi.util.system.activeNetworkState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeExtensionsScreenModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic("eu.kanade.tachiyomi.util.system.NetworkStateTrackerKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("eu.kanade.tachiyomi.util.system.NetworkStateTrackerKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `invalid extension notice is emitted once and late collector receives it`() = runTest {
        val invalid = AnimeLoadResult.Invalid(
            pkgName = "com.example.bad",
            name = "Broken Extension",
            versionName = "1.0.0",
            versionCode = 1L,
            signatureHash = "hash-z",
            reason = InvalidReason.SOURCE_ID_THROW,
        )
        val invalidNotices = MutableSharedFlow<AnimeLoadResult.Invalid>(replay = 2)
        invalidNotices.tryEmit(invalid)
        invalidNotices.tryEmit(invalid)
        val model = createModel(invalidNotices)

        advanceUntilIdle()

        val event = model.events.first()
        assertEquals(AnimeExtensionsScreenModel.Event.InvalidExtensionRevoked(invalid), event)

        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking {
                withTimeout(250) {
                    model.events.first()
                }
            }
        }
    }

    private fun createModel(
        invalidNotices: SharedFlow<AnimeLoadResult.Invalid>,
    ): AnimeExtensionsScreenModel {
        val preferences = SourcePreferences(InMemoryPreferenceStore())
        val basePreferences = mockk<BasePreferences>()
        val installerPreference = mockk<ExtensionInstallerPreference>()
        every { basePreferences.extensionInstaller() } returns installerPreference
        every { installerPreference.changes() } returns emptyFlow()

        val extensionManager = mockk<AnimeExtensionManager>()
        every { extensionManager.invalidExtensionNotices } returns invalidNotices
        coEvery { extensionManager.findAvailableExtensions() } returns Unit

        val getExtensions = mockk<GetAnimeExtensionsByType>()
        every { getExtensions.subscribe() } returns flowOf(
            AnimeExtensions(
                updates = emptyList(),
                installed = emptyList(),
                available = emptyList(),
                untrusted = emptyList(),
            ),
        )

        val network = mockk<NetworkHelper>()
        every { network.nonCloudflareClient } returns OkHttpClient()

        val application = mockk<Application>()
        every { application.activeNetworkState() } returns NetworkState(
            isConnected = true,
            isValidated = true,
            isWifi = false,
        )

        return AnimeExtensionsScreenModel(
            preferences = preferences,
            basePreferences = basePreferences,
            extensionManager = extensionManager,
            getExtensions = getExtensions,
            network = network,
            application = application,
        )
    }
}
