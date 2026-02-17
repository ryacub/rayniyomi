package eu.kanade.tachiyomi.feature.novel

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LightNovelPluginManagerTest {

    private val testSigner = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    @Test
    fun `invalid checksum rejects before install launch`() = runTest {
        var launchCalled = false
        val manager = createManager(
            manifestFetcher = { Result.success(validManifest()) },
            pluginDownloader = { Result.failure(IllegalStateException("checksum validation failed")) },
            installLauncher = {
                launchCalled = true
                Result.success(Unit)
            },
        )

        val result = manager.ensurePluginReady("stable")

        assertEquals(
            LightNovelPluginManager.InstallResult.Rejected(
                LightNovelPluginManager.RejectionReason.CHECKSUM_MISMATCH,
            ),
            result,
        )
        assertFalse(launchCalled)
    }

    @Test
    fun `unsigned archive rejects`() = runTest {
        val archiveInfo = compatiblePackageInfo(LightNovelPluginManager.PLUGIN_PACKAGE_NAME)
        val manager = createManager(
            packageInspector = FakePackageInspector(archive = archiveInfo, signatures = emptyList()),
            manifestFetcher = { Result.success(validManifest()) },
            pluginDownloader = { Result.success(tempApkFile()) },
        )

        val result = manager.ensurePluginReady("stable")

        assertEquals(
            LightNovelPluginManager.InstallResult.Rejected(
                LightNovelPluginManager.RejectionReason.UNSIGNED_PLUGIN_APK,
            ),
            result,
        )
    }

    @Test
    fun `wrong signer rejects`() = runTest {
        val archiveInfo = compatiblePackageInfo(LightNovelPluginManager.PLUGIN_PACKAGE_NAME)
        val manager = createManager(
            packageInspector = FakePackageInspector(
                archive = archiveInfo,
                signatures = listOf("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            ),
            manifestFetcher = { Result.success(validManifest()) },
            pluginDownloader = { Result.success(tempApkFile()) },
        )

        val result = manager.ensurePluginReady("stable")

        assertEquals(
            LightNovelPluginManager.InstallResult.Rejected(
                LightNovelPluginManager.RejectionReason.SIGNER_MISMATCH,
            ),
            result,
        )
    }

    @Test
    fun `package mismatch rejects`() = runTest {
        val archiveInfo = compatiblePackageInfo("xyz.rayniyomi.plugin.other")
        val manager = createManager(
            packageInspector = FakePackageInspector(archive = archiveInfo, signatures = listOf(testSigner)),
            manifestFetcher = { Result.success(validManifest()) },
            pluginDownloader = { Result.success(tempApkFile()) },
        )

        val result = manager.ensurePluginReady("stable")

        assertEquals(
            LightNovelPluginManager.InstallResult.Rejected(
                LightNovelPluginManager.RejectionReason.PACKAGE_NAME_MISMATCH,
            ),
            result,
        )
    }

    @Test
    fun `incompatible plugin API rejects`() = runTest {
        val archiveInfo = packageInfo(LightNovelPluginManager.PLUGIN_PACKAGE_NAME)
        val manager = createManager(
            packageInspector = FakePackageInspector(
                archive = archiveInfo,
                signatures = listOf(testSigner),
                compatibilityState = LightNovelPluginManager.CompatibilityState.API_MISMATCH,
            ),
            manifestFetcher = { Result.success(validManifest()) },
            pluginDownloader = { Result.success(tempApkFile()) },
        )

        val result = manager.ensurePluginReady("stable")

        assertEquals(
            LightNovelPluginManager.InstallResult.Rejected(
                LightNovelPluginManager.RejectionReason.PLUGIN_API_MISMATCH,
            ),
            result,
        )
    }

    @Test
    fun `host too old rejects`() = runTest {
        val archiveInfo = packageInfo(LightNovelPluginManager.PLUGIN_PACKAGE_NAME)
        val manager = createManager(
            packageInspector = FakePackageInspector(
                archive = archiveInfo,
                signatures = listOf(testSigner),
                compatibilityState = LightNovelPluginManager.CompatibilityState.HOST_TOO_OLD,
            ),
            manifestFetcher = { Result.success(validManifest()) },
            pluginDownloader = { Result.success(tempApkFile()) },
        )

        val result = manager.ensurePluginReady("stable")

        assertEquals(
            LightNovelPluginManager.InstallResult.Rejected(
                LightNovelPluginManager.RejectionReason.HOST_VERSION_TOO_OLD,
            ),
            result,
        )
    }

    @Test
    fun `host too new rejects`() = runTest {
        val archiveInfo = packageInfo(LightNovelPluginManager.PLUGIN_PACKAGE_NAME)
        val manager = createManager(
            packageInspector = FakePackageInspector(
                archive = archiveInfo,
                signatures = listOf(testSigner),
                compatibilityState = LightNovelPluginManager.CompatibilityState.HOST_TOO_NEW,
            ),
            manifestFetcher = { Result.success(validManifest()) },
            pluginDownloader = { Result.success(tempApkFile()) },
        )

        val result = manager.ensurePluginReady("stable")

        assertEquals(
            LightNovelPluginManager.InstallResult.Rejected(
                LightNovelPluginManager.RejectionReason.HOST_VERSION_TOO_NEW,
            ),
            result,
        )
    }

    @Test
    fun `valid manifest checksum signer compatibility launches install`() = runTest {
        var launchCalled = false
        val archiveInfo = compatiblePackageInfo(LightNovelPluginManager.PLUGIN_PACKAGE_NAME)
        val manager = createManager(
            packageInspector = FakePackageInspector(archive = archiveInfo, signatures = listOf(testSigner)),
            manifestFetcher = { Result.success(validManifest()) },
            pluginDownloader = { Result.success(tempApkFile()) },
            installLauncher = {
                launchCalled = true
                Result.success(Unit)
            },
        )

        val result = manager.ensurePluginReady("stable")

        assertEquals(LightNovelPluginManager.InstallResult.InstallLaunched, result)
        assertTrue(launchCalled)
    }

    @Test
    fun `isPluginReady false when signer invalid even if compatible`() {
        val installedInfo = compatiblePackageInfo(LightNovelPluginManager.PLUGIN_PACKAGE_NAME)
        val manager = createManager(
            packageInspector = FakePackageInspector(
                installed = installedInfo,
                signatures = listOf("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            ),
        )

        assertFalse(manager.isPluginReady())
    }

    @Test
    fun `missing signer pins causes fail closed rejection`() = runTest {
        val archiveInfo = compatiblePackageInfo(LightNovelPluginManager.PLUGIN_PACKAGE_NAME)
        val manager = createManager(
            packageInspector = FakePackageInspector(archive = archiveInfo, signatures = listOf(testSigner)),
            manifestFetcher = { Result.success(validManifest()) },
            pluginDownloader = { Result.success(tempApkFile()) },
            signerPinsProvider = { emptySet() },
        )

        val result = manager.ensurePluginReady("stable")

        assertEquals(
            LightNovelPluginManager.InstallResult.Rejected(
                LightNovelPluginManager.RejectionReason.MISSING_SIGNER_PINS,
            ),
            result,
        )
    }

    private fun createManager(
        packageInspector: LightNovelPluginManager.PackageInspector = FakePackageInspector(),
        manifestFetcher: suspend (String) -> Result<LightNovelPluginManifest> = {
            Result.failure(IllegalStateException("unused"))
        },
        pluginDownloader: suspend (LightNovelPluginManifest) -> Result<File> = {
            Result.failure(IllegalStateException("unused"))
        },
        installLauncher: suspend (File) -> Result<Unit> = { Result.success(Unit) },
        signerPinsProvider: () -> Set<String> = { setOf(testSigner) },
    ): LightNovelPluginManager {
        val context = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        every { context.cacheDir } returns createTempDir(prefix = "ln-plugin-test")
        every { context.packageManager } returns packageManager

        return LightNovelPluginManager(
            context = context,
            network = mockk(relaxed = true),
            json = mockk(relaxed = true),
            packageInspector = packageInspector,
            manifestFetcherOverride = manifestFetcher,
            pluginDownloaderOverride = pluginDownloader,
            installLauncherOverride = installLauncher,
            signerPinsProviderOverride = signerPinsProvider,
        )
    }

    private fun validManifest(): LightNovelPluginManifest {
        return LightNovelPluginManifest(
            packageName = LightNovelPluginManager.PLUGIN_PACKAGE_NAME,
            versionCode = 10,
            pluginApiVersion = 1,
            minHostVersion = 1,
            targetHostVersion = null,
            apkUrl = "https://example.com/plugin.apk",
            apkSha256 = testSigner,
        )
    }

    private fun compatiblePackageInfo(packageName: String): PackageInfo {
        return packageInfo(packageName = packageName)
    }

    private fun packageInfo(packageName: String): PackageInfo {
        return PackageInfo().apply {
            this.packageName = packageName
        }
    }

    private fun tempApkFile(): File {
        return File.createTempFile("plugin", ".apk").apply {
            writeText("dummy")
            deleteOnExit()
        }
    }

    private class FakePackageInspector(
        private val installed: PackageInfo? = null,
        private val archive: PackageInfo? = null,
        private val signatures: List<String> = emptyList(),
        private val compatibilityState: LightNovelPluginManager.CompatibilityState =
            LightNovelPluginManager.CompatibilityState.READY,
    ) : LightNovelPluginManager.PackageInspector {

        override fun getInstalledPackageInfo(packageName: String): PackageInfo? {
            return installed?.takeIf { it.packageName == packageName }
        }

        override fun getArchivePackageInfo(apkPath: String): PackageInfo? {
            return archive
        }

        override fun getSignatures(packageInfo: PackageInfo): List<String> {
            return signatures
        }

        override fun getCompatibilityState(packageInfo: PackageInfo): LightNovelPluginManager.CompatibilityState {
            return compatibilityState
        }
    }
}
