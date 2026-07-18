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
    fun `install blocked returns Blocked with reason when plugin not installed`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(installed = false),
            installPhase = InstallPhase.IDLE,
            installBlocked = true,
            blockReason = "Not available in release builds",
        )
        assertEquals(LightNovelPluginUiState.Blocked("Not available in release builds"), result)
    }

    @Test
    fun `download phase takes priority over install blocked`() {
        // Once a download is in flight the policy check is irrelevant; show progress.
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(),
            installPhase = InstallPhase.DOWNLOADING,
            installBlocked = true,
            blockReason = "Reason",
        )
        assertEquals(LightNovelPluginUiState.Downloading, result)
    }

    @Test
    fun `installed and ready returns Ready even when install is blocked`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(installed = true, signed = true, compatible = true),
            installPhase = InstallPhase.IDLE,
            installBlocked = true,
            blockReason = "Blocked in release",
        )
        assertEquals(LightNovelPluginUiState.Ready, result)
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

    @Test
    fun `every install error code surfaces its own InstallFailed variant`() {
        // Guards against any code silently collapsing into a generic missing or blocked state.
        LightNovelPluginManager.InstallErrorCode.entries.forEach { code ->
            val result = resolvePluginUiState(
                featureEnabled = true,
                pluginStatus = status(installed = false),
                installPhase = InstallPhase.IDLE,
                installBlocked = false,
                blockReason = null,
                lastInstallError = code,
            )
            assertEquals(LightNovelPluginUiState.InstallFailed(code), result)
        }
    }

    @Test
    fun `install failure surfaces even when install is blocked`() {
        // A concrete failure reason is more actionable than the generic blocked message.
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(installed = false),
            installPhase = InstallPhase.IDLE,
            installBlocked = true,
            blockReason = "Blocked in release",
            lastInstallError = LightNovelPluginManager.InstallErrorCode.DOWNLOAD_FAILED,
        )
        assertEquals(
            LightNovelPluginUiState.InstallFailed(LightNovelPluginManager.InstallErrorCode.DOWNLOAD_FAILED),
            result,
        )
    }

    @Test
    fun `ready plugin ignores stale install error`() {
        // If the plugin became usable, a leftover failure code must not mask Ready.
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(installed = true, signed = true, compatible = true),
            installPhase = InstallPhase.IDLE,
            installBlocked = false,
            blockReason = null,
            lastInstallError = LightNovelPluginManager.InstallErrorCode.DOWNLOAD_FAILED,
        )
        assertEquals(LightNovelPluginUiState.Ready, result)
    }

    @Test
    fun `installed incompatible plugin takes priority over install error`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(installed = true, signed = true, compatible = false),
            installPhase = InstallPhase.IDLE,
            installBlocked = false,
            blockReason = null,
            lastInstallError = LightNovelPluginManager.InstallErrorCode.DOWNLOAD_FAILED,
        )
        assertEquals(LightNovelPluginUiState.Incompatible(IncompatibleReason.API_MISMATCH), result)
    }

    @Test
    fun `active download clears reported failure by taking phase priority`() {
        // A fresh attempt (DOWNLOADING) should show progress, not the previous failure.
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(installed = false),
            installPhase = InstallPhase.DOWNLOADING,
            installBlocked = false,
            blockReason = null,
            lastInstallError = LightNovelPluginManager.InstallErrorCode.DOWNLOAD_FAILED,
        )
        assertEquals(LightNovelPluginUiState.Downloading, result)
    }

    @Test
    fun `no install error falls back to Missing`() {
        val result = resolvePluginUiState(
            featureEnabled = true,
            pluginStatus = status(installed = false),
            installPhase = InstallPhase.IDLE,
            installBlocked = false,
            blockReason = null,
            lastInstallError = null,
        )
        assertEquals(LightNovelPluginUiState.Missing, result)
    }
}
