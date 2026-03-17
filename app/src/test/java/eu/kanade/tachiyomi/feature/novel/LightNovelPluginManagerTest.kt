package eu.kanade.tachiyomi.feature.novel

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Bundle
import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.lang.Hash
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LightNovelPluginManagerTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var network: NetworkHelper
    private lateinit var manager: LightNovelPluginManager

    @BeforeEach
    fun setUp() {
        context = mockk<Context>(relaxed = true)
        packageManager = mockk<PackageManager>(relaxed = true)
        every { context.packageManager } returns packageManager

        // Mock Hash.sha256 so signature bytes can be arbitrary test values
        mockkObject(Hash)
        every { Hash.sha256(any<ByteArray>()) } returns
            "7b7f000000000000000000000000000000000000000000000000000000000000"

        network = mockk<NetworkHelper>(relaxed = true)
        val json = Json { ignoreUnknownKeys = true }
        val preferences = mockk<NovelFeaturePreferences>(relaxed = true)

        manager = LightNovelPluginManager(
            context = context,
            network = network,
            json = json,
            preferences = preferences,
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Hash)
        manager.close()
    }

    private fun createPackageInfoMock(
        installed: Boolean = true,
        versionCode: Int = 1,
        apiVersion: Int = 1,
        minHostVersion: Long = 100L,
        targetHostVersion: Long = 1000L,
        hasValidSignature: Boolean = false,
    ): PackageInfo? {
        if (!installed) {
            return null
        }

        val packageInfo = mockk<PackageInfo>(relaxed = true)
        packageInfo.packageName = "xyz.rayniyomi.plugin.lightnovel"
        packageInfo.versionCode = versionCode

        val appInfo = mockk<ApplicationInfo>(relaxed = true)
        val metaData = mockk<Bundle>(relaxed = true)

        every { metaData.getInt("rayniyomi.plugin.api_version", -1) } returns apiVersion
        every { metaData.getLong("rayniyomi.plugin.min_host_version", Long.MAX_VALUE) } returns minHostVersion
        every { metaData.getLong("rayniyomi.plugin.target_host_version", 0L) } returns targetHostVersion

        appInfo.metaData = metaData
        packageInfo.applicationInfo = appInfo

        if (hasValidSignature) {
            val signature = mockk<Signature>(relaxed = false)
            // Arbitrary bytes; Hash.sha256 is mocked in setUp to return a trusted hash
            every { signature.toByteArray() } returns byteArrayOf(0x01, 0x02, 0x03)

            val signingInfo = mockk<SigningInfo>(relaxed = false)
            every { signingInfo.hasMultipleSigners() } returns false
            every { signingInfo.signingCertificateHistory } returns arrayOf(signature)

            packageInfo.signingInfo = signingInfo
            packageInfo.signatures = arrayOf(signature)
        } else {
            packageInfo.signingInfo = null
            packageInfo.signatures = null
        }

        return packageInfo
    }

    private fun validManifestJson(
        packageName: String = PLUGIN_PACKAGE_NAME,
        versionCode: Long = 2L,
        pluginApiVersion: Int = 1,
        minHostVersion: Long = 100L,
        apkUrl: String = "https://example.com/plugin.apk",
        apkSha256: String = "abc123",
    ): String = """
        {
            "package_name": "$packageName",
            "version_code": $versionCode,
            "plugin_api_version": $pluginApiVersion,
            "min_host_version": $minHostVersion,
            "apk_url": "$apkUrl",
            "apk_sha256": "$apkSha256"
        }
    """.trimIndent()

    private fun createNetworkCallThatFails(): Call {
        val mockCall = mockk<Call>(relaxed = true)
        every { mockCall.enqueue(any()) } answers {
            firstArg<Callback>().onFailure(mockCall, IOException("Network error"))
        }
        return mockCall
    }

    private fun createNetworkCallThatSucceeds(responseBody: String): Call {
        val mockResponseBody = mockk<ResponseBody>(relaxed = true)
        every { mockResponseBody.string() } returns responseBody
        val mockResponse = mockk<Response>(relaxed = true)
        every { mockResponse.body } returns mockResponseBody
        every { mockResponse.isSuccessful } returns true
        val mockCall = mockk<Call>(relaxed = true)
        every { mockCall.enqueue(any()) } answers {
            firstArg<Callback>().onResponse(mockCall, mockResponse)
        }
        return mockCall
    }

    // ===== Plugin Status Queries Tests =====

    @Test
    fun `isPluginReady() returns false when plugin not installed`() {
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } throws
            PackageManager.NameNotFoundException()

        val result = manager.isPluginReady()

        result shouldBe false
    }

    @Test
    fun `isPluginReady() returns false when installed but not signed or trusted`() {
        val packageInfo = createPackageInfoMock(installed = true, hasValidSignature = false)!!
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } returns packageInfo

        val result = manager.isPluginReady()

        result shouldBe false
    }

    @Test
    fun `isPluginReady() returns false when installed but incompatible`() {
        val packageInfo = createPackageInfoMock(
            installed = true,
            hasValidSignature = true,
            apiVersion = 999,
        )!!
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } returns packageInfo

        val result = manager.isPluginReady()

        result shouldBe false
    }

    @Test
    fun `isPluginReady() returns true when installed, signed, and compatible`() {
        val packageInfo = createPackageInfoMock(
            installed = true,
            hasValidSignature = true,
            apiVersion = 1,
            minHostVersion = 100L,
            targetHostVersion = 1000L,
        )!!
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } returns packageInfo

        val result = manager.isPluginReady()

        result shouldBe true
    }

    @Test
    fun `isInstalled() returns false when package not found`() {
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } throws
            PackageManager.NameNotFoundException()

        val result = manager.isInstalled()

        result shouldBe false
    }

    @Test
    fun `isInstalled() returns true when package found`() {
        val packageInfo = createPackageInfoMock(installed = true)!!
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } returns packageInfo

        val result = manager.isInstalled()

        result shouldBe true
    }

    @Test
    fun `getPluginStatus() returns all false when plugin not installed`() {
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } throws
            PackageManager.NameNotFoundException()

        val status = manager.getPluginStatus()

        status.installed shouldBe false
        status.signedAndTrusted shouldBe false
        status.compatible shouldBe false
        status.installedVersionCode shouldBe null
    }

    @Test
    fun `getPluginStatus() populates installed and versionCode when package found`() {
        val packageInfo = createPackageInfoMock(
            installed = true,
            versionCode = 123,
            hasValidSignature = true,
            apiVersion = 1,
        )!!
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } returns packageInfo

        val status = manager.getPluginStatus()

        status.installed shouldBe true
        status.installedVersionCode shouldBe 123L
    }

    @Test
    fun `getPluginStatus() marks signedAndTrusted true when signature is valid`() {
        val packageInfo = createPackageInfoMock(
            installed = true,
            hasValidSignature = true,
            apiVersion = 1,
        )!!
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } returns packageInfo

        val status = manager.getPluginStatus()

        status.signedAndTrusted shouldBe true
    }

    @Test
    fun `getPluginStatus() marks compatible true when versions match`() {
        val packageInfo = createPackageInfoMock(
            installed = true,
            hasValidSignature = true,
            apiVersion = 1,
            minHostVersion = 100L,
            targetHostVersion = 1000L,
        )!!
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } returns packageInfo

        val status = manager.getPluginStatus()

        status.compatible shouldBe true
    }

    // ===== Install Disabled Flow Tests =====

    @Test
    fun `ensurePluginReady() returns Error(INSTALL_DISABLED) when installation disabled`() {
        // BuildConfig.DEBUG is true in debug unit tests, so we use spyk to force
        // isPluginInstallEnabled() = false, simulating a release build with flag off.
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns false

        val result = runBlocking { spyManager.ensurePluginReady(channel = "stable") }

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.INSTALL_DISABLED,
        )
    }

    @Test
    fun `ensurePluginReady() returns Error(INSTALL_DISABLED) error code validation`() {
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns false

        val result = runBlocking { spyManager.ensurePluginReady(channel = "stable") }

        when (result) {
            is LightNovelPluginManager.InstallResult.Error -> {
                result.code shouldBe LightNovelPluginManager.InstallErrorCode.INSTALL_DISABLED
            }
            else -> throw AssertionError("Expected Error result, got $result")
        }
    }

    // ===== Already Ready Flow Tests =====

    @Test
    fun `ensurePluginReady() returns AlreadyReady when plugin is installed signed and compatible`() {
        val packageInfo = createPackageInfoMock(
            installed = true,
            hasValidSignature = true,
            apiVersion = 1,
            minHostVersion = 100L,
            targetHostVersion = 1000L,
        )!!
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } returns packageInfo

        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = runBlocking { spyManager.ensurePluginReady(channel = "stable") }

        result shouldBe LightNovelPluginManager.InstallResult.AlreadyReady
    }

    @Test
    fun `ensurePluginReady() does not make network calls when plugin already ready`() {
        val packageInfo = createPackageInfoMock(
            installed = true,
            hasValidSignature = true,
            apiVersion = 1,
            minHostVersion = 100L,
            targetHostVersion = 1000L,
        )!!
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } returns packageInfo

        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        runBlocking { spyManager.ensurePluginReady(channel = "stable") }

        verify(exactly = 0) { network.client }
    }

    @Test
    fun `ensurePluginReady() returns AlreadyReady regardless of channel when plugin is ready`() {
        val packageInfo = createPackageInfoMock(
            installed = true,
            hasValidSignature = true,
            apiVersion = 1,
            minHostVersion = 100L,
            targetHostVersion = 1000L,
        )!!
        every { packageManager.getPackageInfo(PLUGIN_PACKAGE_NAME, any<Int>()) } returns packageInfo

        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val stableResult = runBlocking { spyManager.ensurePluginReady(channel = "stable") }
        val betaResult = runBlocking { spyManager.ensurePluginReady(channel = "beta") }

        stableResult shouldBe LightNovelPluginManager.InstallResult.AlreadyReady
        betaResult shouldBe LightNovelPluginManager.InstallResult.AlreadyReady
    }

    // ===== Manifest Fetch Flow Tests =====

    @Test
    fun `ensurePluginReady() returns Error(MANIFEST_FETCH_FAILED) when network request fails`() {
        every { network.client.newCall(any()) } returns createNetworkCallThatFails()

        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = runBlocking { spyManager.ensurePluginReady(channel = "stable") }

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.MANIFEST_FETCH_FAILED,
        )
    }

    @Test
    fun `ensurePluginReady() returns Error(MANIFEST_PACKAGE_MISMATCH) when manifest has wrong package name`() {
        every { network.client.newCall(any()) } returns createNetworkCallThatSucceeds(
            validManifestJson(packageName = "xyz.wrong.package"),
        )

        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = runBlocking { spyManager.ensurePluginReady(channel = "stable") }

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.MANIFEST_PACKAGE_MISMATCH,
        )
    }

    @Test
    fun `ensurePluginReady() fetches manifest from stable URL when channel is stable`() {
        val requestSlot = slot<Request>()
        every { network.client.newCall(capture(requestSlot)) } returns createNetworkCallThatFails()

        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        runBlocking { spyManager.ensurePluginReady(channel = "stable") }

        requestSlot.captured.url.toString() shouldBe STABLE_MANIFEST_URL
    }

    @Test
    fun `ensurePluginReady() fetches manifest from beta URL when channel is beta`() {
        val requestSlot = slot<Request>()
        every { network.client.newCall(capture(requestSlot)) } returns createNetworkCallThatFails()

        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        runBlocking { spyManager.ensurePluginReady(channel = "beta") }

        requestSlot.captured.url.toString() shouldBe BETA_MANIFEST_URL
    }

    companion object {
        private const val PLUGIN_PACKAGE_NAME = "xyz.rayniyomi.plugin.lightnovel"
        private const val STABLE_MANIFEST_URL =
            "https://github.com/ryacub/rayniyomi/releases/latest/download/lightnovel-plugin-manifest.json"
        private const val BETA_MANIFEST_URL =
            "https://github.com/ryacub/rayniyomi/releases/download/plugin-beta/lightnovel-plugin-manifest.json"
    }
}
