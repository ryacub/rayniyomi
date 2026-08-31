package eu.kanade.tachiyomi.extension.manga.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.FeatureInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.File

class MangaExtensionLoaderTest {

    @Test
    fun `source factory failures remain retryable`() {
        var primaryAttempts = 0
        var fallbackAttempts = 0

        repeat(2) {
            MangaExtensionLoader.instantiateSources(
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
    fun `resolve lib version uses declared value when present`() {
        MangaExtensionLoader.resolveLibVersion(declaredLibVersion = 1.4f, versionName = "1.4.10") shouldBe 1.4
    }

    @Test
    fun `resolve lib version declared value wins over version name`() {
        MangaExtensionLoader.resolveLibVersion(declaredLibVersion = 1.6f, versionName = "1.4.10") shouldBe 1.6
    }

    @Test
    fun `lib version 1 point 4 is supported`() {
        MangaExtensionLoader.isSupportedLibVersion(1.4) shouldBe true
    }

    @Test
    fun `lib version 1 point 6 is supported`() {
        MangaExtensionLoader.isSupportedLibVersion(1.6) shouldBe true
    }

    @Test
    fun `lib version 1 point 5 is rejected`() {
        MangaExtensionLoader.isSupportedLibVersion(1.5) shouldBe false
    }

    @Test
    fun `null lib version is rejected`() {
        MangaExtensionLoader.isSupportedLibVersion(null) shouldBe false
    }

    @Test
    fun `declared lib version 1 point 6 survives conversion and is accepted`() {
        MangaExtensionLoader.isSupportedLibVersion(
            MangaExtensionLoader.resolveLibVersion(
                declaredLibVersion = 1.6f,
                versionName = "1.6.0",
            ),
        ) shouldBe true
    }

    @Test
    fun `missing metadata returns a named load error without throwing`() = runTest {
        val result = loadExtension(metadata = null)

        result shouldBe MangaLoadResult.Error("Failed to load extension Example Manga: missing metadata")
    }

    @Test
    fun `malformed version metadata returns a named load error`() = runTest {
        val result = loadExtension(Bundle(), versionName = "not-a-version")

        result shouldBe MangaLoadResult.Error("Failed to load extension Example Manga: unsupported library version")
    }

    @Test
    fun `missing required metadata field returns a named load error`() = runTest {
        val result = loadExtension(Bundle())

        result shouldBe MangaLoadResult.Error(
            "Failed to load extension Example Manga: missing source class metadata",
        )
    }

    @Test
    fun `wrong metadata type returns a named load error`() = runTest {
        val metadata = mockk<Bundle>(relaxed = true)
        every { metadata.get("tachiyomi.extension.class") } returns 1
        val result = loadExtension(metadata)

        result shouldBe MangaLoadResult.Error("Failed to load extension Example Manga: malformed metadata")
    }

    @Test
    fun `resolve lib version falls back to version name when declared is zero`() {
        MangaExtensionLoader.resolveLibVersion(declaredLibVersion = 0f, versionName = "1.4.10") shouldBe 1.4
    }

    @Test
    fun `resolve lib version returns null for non numeric version name`() {
        MangaExtensionLoader.resolveLibVersion(declaredLibVersion = 0f, versionName = "banana") shouldBe null
    }

    @Test
    fun `resolve lib version parses single segment version name`() {
        MangaExtensionLoader.resolveLibVersion(declaredLibVersion = 0f, versionName = "1.4") shouldBe 1.0
    }

    @Test
    fun `resolve nsfw false when no warning and no flag`() {
        MangaExtensionLoader.resolveIsNsfw(contentWarning = 0, nsfwFlag = 0) shouldBe false
    }

    @Test
    fun `resolve nsfw true when content warning present`() {
        MangaExtensionLoader.resolveIsNsfw(contentWarning = 1, nsfwFlag = 0) shouldBe true
    }

    @Test
    fun `resolve nsfw true when old nsfw flag present`() {
        MangaExtensionLoader.resolveIsNsfw(contentWarning = 0, nsfwFlag = 1) shouldBe true
    }

    @Test
    fun `resolve nsfw true for graded content warning above one`() {
        MangaExtensionLoader.resolveIsNsfw(contentWarning = 2, nsfwFlag = 0) shouldBe true
    }

    @Test
    fun `resolve nsfw true when both warning and flag present`() {
        MangaExtensionLoader.resolveIsNsfw(contentWarning = 1, nsfwFlag = 1) shouldBe true
    }

    private suspend fun loadExtension(
        metadata: Bundle?,
        versionName: String = "1.4.0",
    ): MangaLoadResult {
        val context = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>(relaxed = true)
        val applicationInfo = ApplicationInfo().apply { this.metaData = metadata }
        val packageInfo = PackageInfo().apply {
            packageName = "com.example.manga"
            this.applicationInfo = applicationInfo
            this.versionName = versionName
            reqFeatures = arrayOf(
                FeatureInfo().apply { name = "tachiyomi.extension" },
            )
        }

        every { context.packageManager } returns packageManager
        every { context.filesDir } returns File("build/tmp/manga-extension-loader-test")
        every { packageManager.getPackageInfo("com.example.manga", any<Int>()) } returns packageInfo
        every { packageManager.getApplicationLabel(applicationInfo) } returns "Example Manga"

        return MangaExtensionLoader.loadMangaExtensionFromPkgName(context, "com.example.manga")
    }
}
