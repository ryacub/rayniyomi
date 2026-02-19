package eu.kanade.tachiyomi.feature.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LightNovelPluginStateReducerTest {

    private fun status(
        installed: Boolean = false,
        signed: Boolean = false,
        compatible: Boolean = false,
    ) = LightNovelPluginManager.PluginStatus(
        installed = installed,
        signedAndTrusted = signed,
        compatible = compatible,
        installedVersionCode = if (installed) 1L else null,
    )

    @Test
    fun `feature disabled returns Disabled regardless of plugin status`() {
        val result = resolvePluginUiState(
            featureEnabled = false,
            pluginStatus = status(installed = true, signed = true, compatible = true),
            installPhase = InstallPhase.IDLE,
            installBlocked = false,
            blockReason = null,
        )
        assertEquals(LightNovelPluginUiState.Disabled, result)
    }

    @Test
    fun `feature disabled overrides active download`() {
        val result = resolvePluginUiState(
            featureEnabled = false,
            pluginStatus = status(),
            installPhase = InstallPhase.DOWNLOADING,
            installBlocked = false,
            blockReason = null,
        )
        assertEquals(LightNovelPluginUiState.Disabled, result)
    }

    @Test
    fun `install blocked returns Blocked with reason`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(),
            installPhase = InstallPhase.IDLE,
            installBlocked = true,
            blockReason = "Not available in release builds",
        )
        assertEquals(LightNovelPluginUiState.Blocked("Not available in release builds"), result)
    }

    @Test
    fun `blocked overrides active download phase`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(),
            installPhase = InstallPhase.DOWNLOADING,
            installBlocked = true,
            blockReason = "Reason",
        )
        assertEquals(LightNovelPluginUiState.Blocked("Reason"), result)
    }

    @Test
    fun `downloading phase returns Downloading`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(),
            installPhase = InstallPhase.DOWNLOADING,
            installBlocked = false,
            blockReason = null,
        )
        assertEquals(LightNovelPluginUiState.Downloading, result)
    }

    @Test
    fun `installing phase returns Installing`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(),
            installPhase = InstallPhase.INSTALLING,
            installBlocked = false,
            blockReason = null,
        )
        assertEquals(LightNovelPluginUiState.Installing, result)
    }

    @Test
    fun `not installed with feature on returns Missing`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(installed = false),
            installPhase = InstallPhase.IDLE,
            installBlocked = false,
            blockReason = null,
        )
        assertEquals(LightNovelPluginUiState.Missing, result)
    }

    @Test
    fun `installed but untrusted returns Incompatible UNTRUSTED`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(installed = true, signed = false),
            installPhase = InstallPhase.IDLE,
            installBlocked = false,
            blockReason = null,
        )
        assertEquals(LightNovelPluginUiState.Incompatible(IncompatibleReason.UNTRUSTED), result)
    }

    @Test
    fun `installed trusted but incompatible API returns Incompatible API_MISMATCH`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(installed = true, signed = true, compatible = false),
            installPhase = InstallPhase.IDLE,
            installBlocked = false,
            blockReason = null,
        )
        assertEquals(LightNovelPluginUiState.Incompatible(IncompatibleReason.API_MISMATCH), result)
    }

    @Test
    fun `all conditions met returns Ready`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(installed = true, signed = true, compatible = true),
            installPhase = InstallPhase.IDLE,
            installBlocked = false,
            blockReason = null,
        )
        assertEquals(LightNovelPluginUiState.Ready, result)
    }
}
