package eu.kanade.tachiyomi.extension.manga.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
    fun `declared lib version 1 point 4 passes the minimum bound`() {
        val libVersion = MangaExtensionLoader.resolveLibVersion(
            declaredLibVersion = 1.4f,
            versionName = "1.4.10",
        )

        (libVersion!! >= MangaExtensionLoader.LIB_VERSION_MIN) shouldBe true
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
}
