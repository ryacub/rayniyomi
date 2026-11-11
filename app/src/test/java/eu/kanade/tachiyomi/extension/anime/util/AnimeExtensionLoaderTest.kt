package eu.kanade.tachiyomi.extension.anime.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AnimeExtensionLoaderTest {

    @Test
    fun `source factory failures remain retryable`() {
        var primaryAttempts = 0
        var fallbackAttempts = 0

        repeat(2) {
            AnimeExtensionLoader.instantiateSources(
                extensionName = "Example",
                sourceClass = "example.Source",
                instantiate = {
                    primaryAttempts++
                    throw LinkageError("incompatible host API")
                },
                fallback = {
                    fallbackAttempts++
                    error("factory failed")
                },
            ) shouldBe null
        }

        primaryAttempts shouldBe 2
        fallbackAttempts shouldBe 2
    }

    @Test
    fun `missing metadata returns a named load error without throwing`() = runTest {
        val result = loadExtension(metadata = null)

        result shouldBe AnimeLoadResult.Error("Failed to load extension Example Anime: missing metadata")
    }

    @Test
    fun `malformed version metadata returns a named load error`() = runTest {
        val result = loadExtension(Bundle(), versionName = "not-a-version")

        result shouldBe AnimeLoadResult.Error("Failed to load extension Example Anime: unsupported library version")
    }

    @Test
    fun `apk loader accepts only supported anime extension libraries`() = runTest {
        val errors = (12..17).associateWith { version ->
            loadExtension(Bundle(), versionName = "$version.0.0")
        }

        val unsupported = AnimeLoadResult.Error("Failed to load extension Example Anime: unsupported library version")
        val accepted = AnimeLoadResult.Error("Failed to load extension Example Anime: missing source class metadata")
        errors shouldBe mapOf(
            12 to unsupported,
            13 to unsupported,
            14 to accepted,
            15 to unsupported,
            16 to accepted,
            17 to accepted,
        )
    }

    @Test
    fun `apk loader uses the declared library version before the package version`() = runTest {
        val metadata = mockk<Bundle>(relaxed = true)
        every { metadata.get("aniyomix.extensionLib") } returns 17

        loadExtension(metadata, versionName = "1.0.0") shouldBe AnimeLoadResult.Error(
            "Failed to load extension Example Anime: missing source class metadata",
        )
    }

    @Test
    fun `missing required metadata field returns a named load error`() = runTest {
        val result = loadExtension(Bundle())

        result shouldBe AnimeLoadResult.Error(
            "Failed to load extension Example Anime: missing source class metadata",
        )
    }

    @Test
    fun `wrong metadata type returns a named load error`() = runTest {
        val metadata = mockk<Bundle>(relaxed = true)
        every { metadata.get("tachiyomi.animeextension.nsfw") } returns "true"
        val result = loadExtension(metadata)

        result shouldBe AnimeLoadResult.Error("Failed to load extension Example Anime: malformed metadata")
    }

    @Test
    fun `each private extension apk is parsed once per scan`(@TempDir filesDir: File) = runTest {
        val extensionDir = File(filesDir, "exts").apply { mkdirs() }
        listOf("com.example.anime.a", "com.example.anime.b", "com.example.anime.c").forEach {
            File(extensionDir, "$it.ext").writeText("")
        }
        val context = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        every { context.filesDir } returns filesDir
        every { context.packageManager } returns packageManager
        every { packageManager.getInstalledPackages(any<Int>()) } returns emptyList()
        every { packageManager.getPackageArchiveInfo(any(), any<Int>()) } answers {
            PackageInfo().apply {
                packageName = File(firstArg<String>()).nameWithoutExtension
                applicationInfo = ApplicationInfo()
                reqFeatures = arrayOf(FeatureInfo().apply { name = "tachiyomi.animeextension" })
            }
        }

        AnimeExtensionLoader.loadExtensions(context)

        verify(exactly = 3) { packageManager.getPackageArchiveInfo(any(), any<Int>()) }
    }

    private suspend fun loadExtension(
        metadata: Bundle?,
        versionName: String = "14.0.0",
    ): AnimeLoadResult {
        val context = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        val applicationInfo = ApplicationInfo().apply { this.metaData = metadata }
        val packageInfo = PackageInfo().apply {
            packageName = "com.example.anime"
            this.applicationInfo = applicationInfo
            this.versionName = versionName
            reqFeatures = arrayOf(
                FeatureInfo().apply { name = "tachiyomi.animeextension" },
            )
        }

        every { context.packageManager } returns packageManager
        every { context.filesDir } returns File("build/tmp/anime-extension-loader-test")
        every { packageManager.getPackageInfo("com.example.anime", any<Int>()) } returns packageInfo
        every { packageManager.getApplicationLabel(applicationInfo) } returns "Example Anime"

        return AnimeExtensionLoader.loadExtensionFromPkgName(context, "com.example.anime")
    }
}
