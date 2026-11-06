package eu.kanade.tachiyomi.extension

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Bundle
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.AnimeSourceFactory
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.extension.anime.api.AnimeExtensionApi
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.manga.api.MangaExtensionApi
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.ui.updates.InMemoryPreferenceStore
import eu.kanade.tachiyomi.util.lang.Hash
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ExtensionUpdateMetadataTest {

    @AfterEach
    fun tearDown() {
        unmockkConstructor(ExtensionUpdateNotifier::class)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `update checks read versions and notify without creating sources`(
        anime: Boolean,
        @TempDir filesDir: File,
    ) = runTest {
        val context = mockk<Context>()
        val packageManager = mockk<PackageManager>()
        val packages = listOf("version", "library").map { extensionPackage(it, anime) }
        every { context.packageManager } returns packageManager
        every { context.filesDir } returns filesDir
        every { context.classLoader } returns javaClass.classLoader
        every { packageManager.getInstalledPackages(any<Int>()) } returns packages
        every { packageManager.getApplicationLabel(any()) } answers { firstArg<ApplicationInfo>().packageName }
        every { testAppGraph.sourcePreferences } returns SourcePreferences(InMemoryPreferenceStore())
        coEvery { testAppGraph.trustAnimeExtension.isTrusted(any(), any()) } returns true
        coEvery { testAppGraph.trustMangaExtension.isTrusted(any(), any()) } returns true
        coEvery { testAppGraph.updateAnimeExtensionRepo.awaitAll() } returns emptyList()
        coEvery { testAppGraph.updateMangaExtensionRepo.awaitAll() } returns emptyList()
        mockkConstructor(ExtensionUpdateNotifier::class)
        every { anyConstructed<ExtensionUpdateNotifier>().promptUpdates(any(), any()) } just runs
        factoryConstructions.set(0)
        sourceCreations.set(0)

        val updates = if (anime) {
            val api = spyk(AnimeExtensionApi())
            coEvery { api.findExtensions() } returns ExtensionCatalog(
                extensions = packages.mapIndexed { index, pkg ->
                    AnimeExtension.Available(
                        name = pkg.packageName,
                        pkgName = pkg.packageName,
                        versionName = "14.0.1",
                        versionCode = if (index == 0) 2 else 1,
                        libVersion = if (index == 0) 14.0 else 15.0,
                        lang = "en",
                        isNsfw = false,
                        sources = emptyList(),
                        apkName = "extension.apk",
                        iconUrl = "",
                        repoUrl = "https://repo.test",
                        signingKeyFingerprint = signatureHash,
                    )
                },
                failedRepoUrls = emptySet(),
            )
            api.checkForUpdates(context).map { it.pkgName }
        } else {
            val api = spyk(MangaExtensionApi())
            coEvery { api.findExtensions() } returns ExtensionCatalog(
                extensions = packages.mapIndexed { index, pkg ->
                    MangaExtension.Available(
                        name = pkg.packageName,
                        pkgName = pkg.packageName,
                        versionName = "1.4.1",
                        versionCode = if (index == 0) 2 else 1,
                        libVersion = if (index == 0) 1.4 else 1.6,
                        lang = "en",
                        isNsfw = false,
                        sources = emptyList(),
                        apkName = "extension.apk",
                        iconUrl = "",
                        repoUrl = "https://repo.test",
                        signingKeyFingerprint = signatureHash,
                    )
                },
                failedRepoUrls = emptySet(),
            )
            api.checkForUpdates(context).map { it.pkgName }
        }

        sourceCreations.get() shouldBe 0
        factoryConstructions.get() shouldBe 0
        updates shouldBe packages.map { it.packageName }
        verify(exactly = 1) {
            anyConstructed<ExtensionUpdateNotifier>().promptUpdates(packages.map { it.packageName }, anime)
        }
    }

    @Suppress("DEPRECATION")
    private fun extensionPackage(name: String, anime: Boolean): PackageInfo {
        val feature = if (anime) "tachiyomi.animeextension" else "tachiyomi.extension"
        val metadata = mockk<Bundle>()
        every { metadata.get(any()) } returns null
        every { metadata.get("$feature.class") } returns if (anime) {
            CountingAnimeFactory::class.java.name
        } else {
            CountingMangaFactory::class.java.name
        }
        return PackageInfo().apply {
            packageName = "example.$name"
            versionName = if (anime) "14.0.0" else "1.4.0"
            versionCode = 1
            applicationInfo = ApplicationInfo().apply {
                packageName = "example.$name"
                sourceDir = "fixture.apk"
                metaData = metadata
            }
            reqFeatures = arrayOf(FeatureInfo().apply { this.name = feature })
            signatures = arrayOf(mockk<Signature> { every { toByteArray() } returns signatureBytes })
        }
    }
}

private val signatureBytes = byteArrayOf(1, 2, 3)
private val signatureHash = Hash.sha256(signatureBytes)
private val factoryConstructions = AtomicInteger()
private val sourceCreations = AtomicInteger()

class CountingAnimeFactory : AnimeSourceFactory {
    init {
        factoryConstructions.incrementAndGet()
    }

    override fun createSources(): List<AnimeSource> {
        sourceCreations.incrementAndGet()
        return emptyList()
    }
}

class CountingMangaFactory : SourceFactory {
    init {
        factoryConstructions.incrementAndGet()
    }

    override fun createSources(): List<MangaSource> {
        sourceCreations.incrementAndGet()
        return emptyList()
    }
}
