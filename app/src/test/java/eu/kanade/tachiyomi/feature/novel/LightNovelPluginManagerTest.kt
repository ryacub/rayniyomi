package eu.kanade.tachiyomi.feature.novel

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.domain.novel.ReleaseChannel
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.lang.Hash
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class LightNovelPluginManagerTest {

    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var network: NetworkHelper
    private lateinit var preferences: NovelFeaturePreferences
    private lateinit var manager: LightNovelPluginManager

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkConstructor(Intent::class)
        val stubIntent = mockk<Intent>(relaxed = true)
        every { anyConstructed<Intent>().setDataAndType(any(), any()) } returns stubIntent
        every { anyConstructed<Intent>().setFlags(any()) } returns stubIntent
        mockkStatic(FileProvider::class)
        context = mockk<Context>(relaxed = true)
        packageManager = mockk<PackageManager>(relaxed = true)
        every { context.packageManager } returns packageManager

        // Mock Hash.sha256 so signature bytes can be arbitrary test values
        mockkObject(Hash)
        every { Hash.sha256(any<ByteArray>()) } returns
            "7b7f000000000000000000000000000000000000000000000000000000000000"

        network = mockk<NetworkHelper>(relaxed = true)
        val json = Json { ignoreUnknownKeys = true }
        preferences = mockk<NovelFeaturePreferences>(relaxed = true)

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
        unmockkStatic(FileProvider::class)
        unmockkConstructor(Intent::class)
        Dispatchers.resetMain()
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
        targetHostVersion: Long = 0L,
        minPluginVersionCode: Long = 0L,
        releaseChannel: String = "stable",
        apkUrl: String = "https://example.com/plugin.apk",
        apkSha256: String = "abc123",
    ): String = """
        {
            "package_name": "$packageName",
            "version_code": $versionCode,
            "plugin_api_version": $pluginApiVersion,
            "min_host_version": $minHostVersion,
            "target_host_version": $targetHostVersion,
            "min_plugin_version_code": $minPluginVersionCode,
            "release_channel": "$releaseChannel",
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

    // APK download: mocks byteStream() with an empty InputStream so real file I/O writes 0 bytes.
    // SHA256 of the resulting empty file = SHA256_EMPTY_BYTES.
    private fun createApkDownloadCallThatSucceeds(): Call {
        val mockResponseBody = mockk<ResponseBody>(relaxed = true)
        every { mockResponseBody.byteStream() } returns ByteArrayInputStream(byteArrayOf())
        val mockResponse = mockk<Response>(relaxed = true)
        every { mockResponse.body } returns mockResponseBody
        every { mockResponse.isSuccessful } returns true
        val mockCall = mockk<Call>(relaxed = true)
        every { mockCall.enqueue(any()) } answers {
            firstArg<Callback>().onResponse(mockCall, mockResponse)
        }
        return mockCall
    }

    // Sets up the full pipeline through POLICY and download so tests can focus on later stages.
    private fun stubForInstallStage() {
        every { preferences.releaseChannel() } returns mockk { every { get() } returns ReleaseChannel.STABLE }
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir"))
        every { network.client.newCall(match { it.url.toString().contains("manifest") }) } returns
            createNetworkCallThatSucceeds(validManifestJson(apkSha256 = SHA256_EMPTY_BYTES))
        every { network.client.newCall(match { it.url.toString().endsWith(".apk") }) } returns
            createApkDownloadCallThatSucceeds()
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
    fun `ensurePluginReady() returns Error(INSTALL_DISABLED) when installation disabled`() = runTest {
        // BuildConfig.DEBUG is true in debug unit tests, so we use spyk to force
        // isPluginInstallEnabled() = false, simulating a release build with flag off.
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns false

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.INSTALL_DISABLED,
        )
    }

    // ===== Already Ready Flow Tests =====

    @Test
    fun `ensurePluginReady() returns AlreadyReady when plugin is installed signed and compatible`() = runTest {
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

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.AlreadyReady
    }

    @Test
    fun `ensurePluginReady() does not make network calls when plugin already ready`() = runTest {
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

        spyManager.ensurePluginReady(channel = "stable")

        verify(exactly = 0) { network.client }
    }

    @Test
    fun `ensurePluginReady() returns AlreadyReady regardless of channel when plugin is ready`() = runTest {
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

        val stableResult = spyManager.ensurePluginReady(channel = "stable")
        val betaResult = spyManager.ensurePluginReady(channel = "beta")

        stableResult shouldBe LightNovelPluginManager.InstallResult.AlreadyReady
        betaResult shouldBe LightNovelPluginManager.InstallResult.AlreadyReady
    }

    // ===== Manifest Fetch Flow Tests =====

    @Test
    fun `ensurePluginReady() returns Error(MANIFEST_FETCH_FAILED) when network request fails`() = runTest {
        every { network.client.newCall(any()) } returns createNetworkCallThatFails()

        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.MANIFEST_FETCH_FAILED,
        )
    }

    @Test
    fun `ensurePluginReady returns Error(MANIFEST_PACKAGE_MISMATCH) when manifest has wrong package name`() = runTest {
        every { network.client.newCall(any()) } returns createNetworkCallThatSucceeds(
            validManifestJson(packageName = "xyz.wrong.package"),
        )

        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.MANIFEST_PACKAGE_MISMATCH,
        )
    }

    @Test
    fun `ensurePluginReady() fetches manifest from stable URL when channel is stable`() = runTest {
        val requestSlot = slot<Request>()
        every { network.client.newCall(capture(requestSlot)) } returns createNetworkCallThatFails()

        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        spyManager.ensurePluginReady(channel = "stable")

        requestSlot.captured.url.toString() shouldBe STABLE_MANIFEST_URL
    }

    @Test
    fun `ensurePluginReady() fetches manifest from beta URL when channel is beta`() = runTest {
        val requestSlot = slot<Request>()
        every { network.client.newCall(capture(requestSlot)) } returns createNetworkCallThatFails()

        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        spyManager.ensurePluginReady(channel = "beta")

        requestSlot.captured.url.toString() shouldBe BETA_MANIFEST_URL
    }

    // ===== Manifest Compatibility Verification Flow Tests =====

    @Test
    fun `ensurePluginReady() returns Error(MANIFEST_API_MISMATCH) when plugin API version is wrong`() = runTest {
        every { network.client.newCall(any()) } returns createNetworkCallThatSucceeds(
            validManifestJson(pluginApiVersion = 2),
        )
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.MANIFEST_API_MISMATCH,
        )
    }

    @Test
    fun `ensurePluginReady() returns Error(MANIFEST_HOST_TOO_OLD) when host version is below minimum`() = runTest {
        every { network.client.newCall(any()) } returns createNetworkCallThatSucceeds(
            validManifestJson(minHostVersion = 999L),
        )
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.MANIFEST_HOST_TOO_OLD,
        )
    }

    @Test
    fun `ensurePluginReady() returns Error(MANIFEST_HOST_TOO_NEW) when host version exceeds target`() = runTest {
        every { network.client.newCall(any()) } returns createNetworkCallThatSucceeds(
            validManifestJson(targetHostVersion = 100L),
        )
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.MANIFEST_HOST_TOO_NEW,
        )
    }

    // ===== Plugin Update Policy Evaluation Tests =====

    @Test
    fun `ensurePluginReady() returns Error(MANIFEST_PLUGIN_TOO_OLD) when plugin version is below minimum`() = runTest {
        every { preferences.releaseChannel() } returns mockk { every { get() } returns ReleaseChannel.STABLE }
        every { network.client.newCall(any()) } returns createNetworkCallThatSucceeds(
            validManifestJson(versionCode = 1L, minPluginVersionCode = 5L),
        )
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.MANIFEST_PLUGIN_TOO_OLD,
        )
    }

    @Test
    fun `ensurePluginReady() returns Error(MANIFEST_WRONG_CHANNEL) when stable host rejects beta plugin`() = runTest {
        every { preferences.releaseChannel() } returns mockk { every { get() } returns ReleaseChannel.STABLE }
        every { network.client.newCall(any()) } returns createNetworkCallThatSucceeds(
            validManifestJson(releaseChannel = "beta"),
        )
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.MANIFEST_WRONG_CHANNEL,
        )
    }

    // ===== Plugin Download Flow Tests =====

    @Test
    fun `ensurePluginReady() returns Error(DOWNLOAD_FAILED) when APK download fails`() = runTest {
        every { preferences.releaseChannel() } returns mockk { every { get() } returns ReleaseChannel.STABLE }
        every { network.client.newCall(match { it.url.toString().contains("manifest") }) } returns
            createNetworkCallThatSucceeds(validManifestJson())
        every { network.client.newCall(match { it.url.toString().endsWith(".apk") }) } returns
            createNetworkCallThatFails()
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.DOWNLOAD_FAILED,
        )
    }

    @Test
    fun `ensurePluginReady() returns Error(DOWNLOAD_FAILED) when APK checksum does not match`() = runTest {
        every { preferences.releaseChannel() } returns mockk { every { get() } returns ReleaseChannel.STABLE }
        every { context.cacheDir } returns File(System.getProperty("java.io.tmpdir"))
        every { network.client.newCall(match { it.url.toString().contains("manifest") }) } returns
            createNetworkCallThatSucceeds(validManifestJson(apkSha256 = "deadbeef"))
        every { network.client.newCall(match { it.url.toString().endsWith(".apk") }) } returns
            createApkDownloadCallThatSucceeds()
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.DOWNLOAD_FAILED,
        )
    }

    // ===== Archive Package Validation Flow Tests =====

    @Test
    fun `ensurePluginReady() returns Error(INVALID_PLUGIN_APK) when package archive is invalid`() = runTest {
        stubForInstallStage()
        every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } returns null
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.INVALID_PLUGIN_APK,
        )
    }

    @Test
    fun `ensurePluginReady() returns Error(ARCHIVE_PACKAGE_MISMATCH) when archive has wrong package name`() = runTest {
        stubForInstallStage()
        val wrongArchive = mockk<PackageInfo>(relaxed = true)
        wrongArchive.packageName = "xyz.wrong.package"
        every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } returns wrongArchive
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.ARCHIVE_PACKAGE_MISMATCH,
        )
    }

    // ===== Install Launch Flow Tests =====

    @Test
    fun `ensurePluginReady() returns InstallLaunched when APK installs successfully`() = runTest {
        stubForInstallStage()
        val validArchive = mockk<PackageInfo>(relaxed = true)
        validArchive.packageName = PLUGIN_PACKAGE_NAME
        every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } returns validArchive
        every { FileProvider.getUriForFile(any(), any(), any()) } returns mockk<Uri>()
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.InstallLaunched
    }

    @Test
    fun `ensurePluginReady() returns Error(INSTALL_LAUNCH_FAILED) when startActivity throws`() = runTest {
        stubForInstallStage()
        val validArchive = mockk<PackageInfo>(relaxed = true)
        validArchive.packageName = PLUGIN_PACKAGE_NAME
        every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } returns validArchive
        every { FileProvider.getUriForFile(any(), any(), any()) } returns mockk<Uri>()
        every { context.startActivity(any()) } throws ActivityNotFoundException("No activity found")
        val spyManager = spyk(manager)
        every { spyManager.isPluginInstallEnabled() } returns true

        val result = spyManager.ensurePluginReady(channel = "stable")

        result shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.INSTALL_LAUNCH_FAILED,
        )
    }

    // ===== In-Flight Install Deduplication Tests =====

    @Test
    fun `ensurePluginReady() deduplicates concurrent in-flight installs`() = runTest {
        val manifestCallCount = AtomicInteger(0)
        val firstCallAtNetworkLatch = CountDownLatch(1)
        val manifestFetchGate = CountDownLatch(1)

        every { network.client.newCall(any()) } answers {
            val call = mockk<Call>(relaxed = true)
            every { call.enqueue(any()) } answers {
                manifestCallCount.incrementAndGet()
                firstCallAtNetworkLatch.countDown()
                manifestFetchGate.await(5, TimeUnit.SECONDS)
                firstArg<Callback>().onFailure(call, IOException("Dedup gate released"))
            }
            call
        }

        // Launch both coroutines on IO threads for true concurrent execution.
        val deferred1 = async(Dispatchers.IO) { manager.ensurePluginReady("stable") }
        // Wait until deferred1 has started the network call — at this point activeDeferred
        // is guaranteed to be set (it is stored before the network call begins).
        firstCallAtNetworkLatch.await(5, TimeUnit.SECONDS)

        val deferred2 = async(Dispatchers.IO) { manager.ensurePluginReady("stable") }
        // Allow deferred2 to find the active deferred and block on its await().
        // A short sleep is unavoidable here: we need deferred2 to be waiting on the deferred
        // before we release the gate, but there is no production-code hook to signal this.
        Thread.sleep(50)

        manifestFetchGate.countDown()
        val results = awaitAll(deferred1, deferred2)

        manifestCallCount.get() shouldBe 1
        results[0] shouldBe results[1]
    }

    @Test
    fun `ensurePluginReady() completed install deferred is not reused on next call`() = runTest {
        var networkCallCount = 0
        every { network.client.newCall(any()) } answers {
            networkCallCount++
            createNetworkCallThatFails()
        }

        manager.ensurePluginReady("stable")
        manager.ensurePluginReady("stable")

        networkCallCount shouldBe 2
    }

    // ===== Error State Recovery Tests =====

    @Test
    fun `ensurePluginReady() retries from scratch after previous error`() = runTest {
        every { network.client.newCall(any()) } returns createNetworkCallThatFails()
        val result1 = manager.ensurePluginReady("stable")
        result1 shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.MANIFEST_FETCH_FAILED,
        )

        // Swap mock: different result proves a fresh network call was made, not a cached error
        every { network.client.newCall(any()) } returns createNetworkCallThatSucceeds(
            validManifestJson(packageName = "xyz.wrong.package"),
        )
        val result2 = manager.ensurePluginReady("stable")
        result2 shouldBe LightNovelPluginManager.InstallResult.Error(
            LightNovelPluginManager.InstallErrorCode.MANIFEST_PACKAGE_MISMATCH,
        )
    }

    @Test
    fun `ensurePluginReady() cleans up APK file when download checksum does not match`() = runTest {
        val tmpDir = File(System.getProperty("java.io.tmpdir"))
        every { context.cacheDir } returns tmpDir
        every { preferences.releaseChannel() } returns mockk { every { get() } returns ReleaseChannel.STABLE }
        every { network.client.newCall(match { it.url.toString().contains("manifest") }) } returns
            createNetworkCallThatSucceeds(validManifestJson(apkSha256 = "deadbeef"))
        every { network.client.newCall(match { it.url.toString().endsWith(".apk") }) } returns
            createApkDownloadCallThatSucceeds()

        manager.ensurePluginReady("stable")

        File(tmpDir, "lightnovel-plugin.apk").exists() shouldBe false
    }

    // ===== Orphaned APK Cleanup Tests =====

    @Test
    fun `LightNovelPluginManager constructor removes existing orphaned APK file`() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"))
        every { context.cacheDir } returns tmpDir

        val apkFile = File(tmpDir, "lightnovel-plugin.apk")
        apkFile.writeBytes(byteArrayOf(1, 2, 3))
        apkFile.exists() shouldBe true

        val freshManager = LightNovelPluginManager(
            context = context,
            network = network,
            json = Json { ignoreUnknownKeys = true },
            preferences = preferences,
        )
        freshManager.close()

        apkFile.exists() shouldBe false
    }

    @Test
    fun `uninstallPlugin() removes existing orphaned APK file`() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"))
        every { context.cacheDir } returns tmpDir

        val apkFile = File(tmpDir, "lightnovel-plugin.apk")
        apkFile.writeBytes(byteArrayOf(1, 2, 3))
        apkFile.exists() shouldBe true

        manager.uninstallPlugin()

        apkFile.exists() shouldBe false
    }

    @Test
    fun `uninstallPlugin() does not throw when APK file does not exist`() {
        val tmpDir = File(System.getProperty("java.io.tmpdir"))
        every { context.cacheDir } returns tmpDir

        File(tmpDir, "lightnovel-plugin.apk").delete()

        // Should not throw
        manager.uninstallPlugin()
    }

    companion object {
        private const val PLUGIN_PACKAGE_NAME = "xyz.rayniyomi.plugin.lightnovel"
        private const val STABLE_MANIFEST_URL =
            "https://github.com/ryacub/rayniyomi/releases/latest/download/lightnovel-plugin-manifest.json"
        private const val BETA_MANIFEST_URL =
            "https://github.com/ryacub/rayniyomi/releases/download/plugin-beta/lightnovel-plugin-manifest.json"

        // SHA-256 of an empty byte array / empty file
        private const val SHA256_EMPTY_BYTES =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
