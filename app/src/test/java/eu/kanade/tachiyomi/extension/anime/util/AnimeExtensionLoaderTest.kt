package eu.kanade.tachiyomi.extension.anime.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

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
}
