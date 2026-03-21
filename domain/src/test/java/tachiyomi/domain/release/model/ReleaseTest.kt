package tachiyomi.domain.release.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ReleaseTest {

    @ParameterizedTest
    @CsvSource(
        "STABLE, false, true", // STABLE + includePrerelease=false → true
        "STABLE, true, true", // STABLE + includePrerelease=true → true
        "PRERELEASE, false, false", // PRERELEASE + includePrerelease=false → false
        "PRERELEASE, true, true", // PRERELEASE + includePrerelease=true → true
    )
    fun `isUsable filters by quality and includePrerelease setting`(
        quality: ReleaseQuality,
        includePrerelease: Boolean,
        expected: Boolean,
    ) {
        val release = Release(
            version = "1.0.0",
            info = "Test release",
            releaseLink = "https://example.com/release/1.0.0",
            downloadLink = "https://example.com/download/1.0.0",
            quality = quality,
        )

        val result = release.isUsable(includePrerelease)

        result shouldBe expected
    }

    @Test
    fun `isUsable returns true for stable release by default`() {
        val release = Release(
            version = "1.0.0",
            info = "Stable release",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/download",
            quality = ReleaseQuality.STABLE,
        )

        release.isUsable(includePrerelease = false) shouldBe true
    }

    @Test
    fun `isUsable returns false for prerelease when includePrerelease is false`() {
        val release = Release(
            version = "1.0.0-rc1",
            info = "Release candidate",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/download",
            quality = ReleaseQuality.PRERELEASE,
        )

        release.isUsable(includePrerelease = false) shouldBe false
    }

    @Test
    fun `isUsable returns true for prerelease when includePrerelease is true`() {
        val release = Release(
            version = "1.0.0-rc1",
            info = "Release candidate",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/download",
            quality = ReleaseQuality.PRERELEASE,
        )

        release.isUsable(includePrerelease = true) shouldBe true
    }
}
