package eu.kanade.tachiyomi.feature.novel

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import eu.kanade.domain.novel.NovelFeaturePreferences
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference

class LightNovelPluginStateManagerTest {

    @AfterEach
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `install failures retain their exact code in ui state`() = runBlocking {
        val manager = createStateManager()

        try {
            listOf(
                LightNovelPluginManager.InstallErrorCode.MANIFEST_API_MISMATCH,
                LightNovelPluginManager.InstallErrorCode.DOWNLOAD_FAILED,
            ).forEach { code ->
                manager.onInstallFailed(code)
                manager.uiState.awaitState(LightNovelPluginUiState.InstallFailed(code))
            }
        } finally {
            manager.close()
        }
    }

    @Test
    fun `download retry supersedes the prior install failure`() = runBlocking {
        val manager = createStateManager()

        try {
            manager.onInstallFailed(LightNovelPluginManager.InstallErrorCode.DOWNLOAD_FAILED)
            manager.uiState.awaitState(
                LightNovelPluginUiState.InstallFailed(LightNovelPluginManager.InstallErrorCode.DOWNLOAD_FAILED),
            )

            manager.onDownloadStarted()
            manager.uiState.awaitState(LightNovelPluginUiState.Downloading)

            manager.onInstallIdle()
            manager.uiState.awaitState(LightNovelPluginUiState.Missing)
        } finally {
            manager.close()
        }
    }

    @Test
    fun `returns true for plugin package added`() {
        assertTrue(
            isLightNovelPluginPackageChange(
                action = Intent.ACTION_PACKAGE_ADDED,
                packageName = LightNovelPluginManager.PLUGIN_PACKAGE_NAME,
            ),
        )
    }

    @Test
    fun `returns true for plugin package removed`() {
        assertTrue(
            isLightNovelPluginPackageChange(
                action = Intent.ACTION_PACKAGE_REMOVED,
                packageName = LightNovelPluginManager.PLUGIN_PACKAGE_NAME,
            ),
        )
    }

    @Test
    fun `returns false for different package`() {
        assertFalse(
            isLightNovelPluginPackageChange(
                action = Intent.ACTION_PACKAGE_ADDED,
                packageName = "com.example.other",
            ),
        )
    }

    @Test
    fun `returns false for unrelated action`() {
        assertFalse(
            isLightNovelPluginPackageChange(
                action = Intent.ACTION_PACKAGE_CHANGED,
                packageName = LightNovelPluginManager.PLUGIN_PACKAGE_NAME,
            ),
        )
    }

    private fun createStateManager(): LightNovelPluginStateManager {
        mockkStatic(ContextCompat::class)
        every {
            ContextCompat.registerReceiver(any(), any(), any(), any<Int>())
        } returns null

        val enabledPreference = mockk<Preference<Boolean>> {
            every { changes() } returns flowOf(true)
        }
        val preferences = mockk<NovelFeaturePreferences> {
            every { enableLightNovels() } returns enabledPreference
        }
        val pluginManager = mockk<LightNovelPluginManager>(relaxed = true) {
            every { getPluginStatus() } returns LightNovelPluginManager.PluginStatus(
                installed = false,
                signedAndTrusted = false,
                compatible = false,
                installedVersionCode = null,
            )
            every { isPluginInstallEnabled() } returns true
        }

        return LightNovelPluginStateManager(
            appContext = mockk<Context>(relaxed = true),
            pluginManager = pluginManager,
            preferences = preferences,
            lifecycle = mockk<Lifecycle>(relaxed = true),
        )
    }

    private suspend fun kotlinx.coroutines.flow.StateFlow<LightNovelPluginUiState>.awaitState(
        expected: LightNovelPluginUiState,
    ) {
        withTimeout(5_000) { first { it == expected } } shouldBe expected
    }
}
