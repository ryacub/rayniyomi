package eu.kanade.tachiyomi.extension.manga

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.core.content.ContextCompat
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.extension.ExtensionUpdateNotifier
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import eu.kanade.tachiyomi.extension.manga.util.MangaExtensionLoader
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.MangaSource
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

class MangaExtensionManagerStateTest {

    private val context = mockk<Context>(relaxed = true)
    private val receiver = slot<BroadcastReceiver>()
    private val server = MockWebServer()
    private val repoResponses = mutableMapOf<String, MockResponse>()

    @BeforeEach
    fun setUp() {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.registerReceiver(any(), capture(receiver), any(), any<Int>()) } returns null
        mockkObject(MangaExtensionLoader)
        testAppGraph
        mockkConstructor(ExtensionUpdateNotifier::class)
        every { anyConstructed<ExtensionUpdateNotifier>().dismiss() } just Runs

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                repoResponses[request.path] ?: MockResponse().setResponseCode(404)
        }
        server.start()
        every { testAppGraph.networkHelper } returns mockk<NetworkHelper> { every { client } returns OkHttpClient() }
        every { testAppGraph.json } returns Json { ignoreUnknownKeys = true }
        every { testAppGraph.getMangaExtensionRepo } returns mockk<GetMangaExtensionRepo> {
            coEvery { getAll() } returns listOf(repo("a"), repo("b"))
        }
    }

    @AfterEach
    fun tearDown() {
        every { testAppGraph.networkHelper } returns mockk(relaxed = true)
        every { testAppGraph.json } returns mockk(relaxed = true)
        every { testAppGraph.getMangaExtensionRepo } returns mockk(relaxed = true)
        server.shutdown()
        unmockkAll()
    }

    @Test
    fun `source icon loads package info once until the extension updates`() = runBlocking<Unit> {
        coEvery { MangaExtensionLoader.loadMangaExtensions(any()) } returns
            listOf(MangaLoadResult.Success(installed(PKG_A, versionCode = 1)))
        every { MangaExtensionLoader.getMangaExtensionPackageInfoFromPkgName(any(), PKG_A) } returns iconPackage()
        coEvery { MangaExtensionLoader.loadMangaExtensionFromPkgName(any(), PKG_A) } returns
            MangaLoadResult.Success(installed(PKG_A, versionCode = 2))
        val manager = initializedManager()

        manager.getAppIconForSource(sourceId(PKG_A))
        manager.getAppIconForSource(sourceId(PKG_A))
        receiver.captured.onReceive(context, intent(Intent.ACTION_PACKAGE_REPLACED, PKG_A))
        withTimeout(5.seconds) {
            manager.installedExtensionsFlow.first { it.singleOrNull()?.versionCode == 2L }
        }
        manager.getAppIconForSource(sourceId(PKG_A))

        verify(exactly = 2) { MangaExtensionLoader.getMangaExtensionPackageInfoFromPkgName(any(), PKG_A) }
    }

    @Test
    fun `an install that lands during an availability refresh stays installed`() = runBlocking<Unit> {
        lateinit var manager: MangaExtensionManager
        val installFired = AtomicBoolean(false)
        val existing = spyk(installed(PKG_A)) {
            every { libVersion } answers {
                if (installFired.compareAndSet(false, true)) installDuringRefresh(manager)
                callOriginal()
            }
        }
        coEvery { MangaExtensionLoader.loadMangaExtensions(any()) } returns listOf(MangaLoadResult.Success(existing))
        coEvery { MangaExtensionLoader.loadMangaExtensionFromPkgName(any(), PKG_NEW) } returns
            MangaLoadResult.Success(installed(PKG_NEW))
        serveIndex("a", PKG_A)
        serveIndex("b")
        manager = initializedManager()

        manager.findAvailableExtensions()
        withTimeoutOrNull(5.seconds) {
            manager.installedExtensionsFlow.first { extensions -> extensions.any { it.pkgName == PKG_NEW } }
        }

        installFired.get() shouldBe true
        manager.installedExtensionsFlow.value.map { it.pkgName } shouldContain PKG_NEW
    }

    @Test
    fun `a failed repo does not mark its extensions obsolete`() = runBlocking<Unit> {
        coEvery { MangaExtensionLoader.loadMangaExtensions(any()) } returns
            listOf(MangaLoadResult.Success(installed(PKG_A)), MangaLoadResult.Success(installed(PKG_B)))
        serveIndex("a", PKG_A)
        repoResponses["/b/repo.json"] = MockResponse().setResponseCode(500)
        val manager = initializedManager()

        manager.findAvailableExtensions()

        obsoleteFlag(manager, PKG_B) shouldBe false
    }

    @Test
    fun `an extension missing from every loaded repo is obsolete`() = runBlocking<Unit> {
        coEvery { MangaExtensionLoader.loadMangaExtensions(any()) } returns
            listOf(MangaLoadResult.Success(installed(PKG_A)), MangaLoadResult.Success(installed(PKG_B)))
        serveIndex("a", PKG_A)
        serveIndex("b")
        val manager = initializedManager()

        manager.findAvailableExtensions()

        obsoleteFlag(manager, PKG_B) shouldBe true
    }

    private fun installDuringRefresh(manager: MangaExtensionManager) = runBlocking {
        receiver.captured.onReceive(context, intent(Intent.ACTION_PACKAGE_ADDED, PKG_NEW))
        withTimeout(5.seconds) {
            manager.installedExtensionsFlow.first { extensions -> extensions.any { it.pkgName == PKG_NEW } }
        }
    }

    private suspend fun obsoleteFlag(manager: MangaExtensionManager, pkgName: String): Boolean {
        val extensions = withTimeoutOrNull(5.seconds) {
            manager.installedExtensionsFlow.first { extensions -> extensions.any { it.repoUrl != null } }
        } ?: manager.installedExtensionsFlow.value
        return extensions.single { it.pkgName == pkgName }.isObsolete
    }

    private suspend fun initializedManager(): MangaExtensionManager {
        val preferences = mockk<SourcePreferences>(relaxed = true) {
            every { enabledLanguages().isSet() } returns true
        }
        val manager = MangaExtensionManager(
            context = context,
            preferences = preferences,
            trustExtension = mockk(relaxed = true),
        )
        withTimeout(5.seconds) { manager.isInitialized.first { it } }
        return manager
    }

    private fun repo(path: String) = ExtensionRepo(
        baseUrl = server.url("/$path").toString(),
        name = path,
        shortName = null,
        website = "",
        signingKeyFingerprint = "hash",
    )

    private fun serveIndex(path: String, vararg pkgNames: String) {
        val body = pkgNames.joinToString(prefix = "[", postfix = "]") { pkgName ->
            """{"name":"Tachiyomi: $pkgName","pkg":"$pkgName","apk":"$pkgName.apk","lang":"en",""" +
                """"code":1,"version":"1.4.1","nsfw":0,"sources":[]}"""
        }
        repoResponses["/$path/index.min.json"] = MockResponse().setBody(body)
    }

    private fun intent(intentAction: String, pkgName: String): Intent = mockk {
        every { action } returns intentAction
        every { getBooleanExtra(Intent.EXTRA_REPLACING, false) } returns false
        every { data } returns mockk<Uri> { every { encodedSchemeSpecificPart } returns pkgName }
    }

    private fun iconPackage() = PackageInfo().apply {
        applicationInfo = mockk<ApplicationInfo> { every { loadIcon(any()) } returns mockk() }
    }

    private fun sourceId(pkgName: String) = pkgName.hashCode().toLong()

    private fun installed(pkgName: String, versionCode: Long = 1) = MangaExtension.Installed(
        name = pkgName,
        pkgName = pkgName,
        versionName = "1.4.$versionCode",
        versionCode = versionCode,
        libVersion = 1.4,
        lang = "en",
        isNsfw = false,
        pkgFactory = null,
        sources = listOf(mockk<MangaSource> { every { id } returns sourceId(pkgName) }),
        icon = null,
        isShared = true,
        signatureHash = "hash",
    )

    private companion object {
        const val PKG_A = "com.example.a"
        const val PKG_B = "com.example.b"
        const val PKG_NEW = "com.example.new"
    }
}
