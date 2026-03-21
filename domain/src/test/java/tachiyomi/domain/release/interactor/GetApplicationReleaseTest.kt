package tachiyomi.domain.release.interactor

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.release.model.Release
import tachiyomi.domain.release.model.ReleaseQuality
import tachiyomi.domain.release.service.ReleaseService
import java.time.Instant

class GetApplicationReleaseTest {

    private lateinit var getApplicationRelease: GetApplicationRelease
    private lateinit var releaseService: ReleaseService
    private lateinit var preference: Preference<Long>

    @BeforeEach
    fun beforeEach() {
        val preferenceStore = mockk<PreferenceStore>()
        preference = mockk()
        every { preferenceStore.getLong(any(), any()) } returns preference
        releaseService = mockk()

        getApplicationRelease = GetApplicationRelease(releaseService, preferenceStore)
    }

    @Test
    fun `When has update but is preview expect new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "r2000",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = true,
                commitCount = 1000,
                versionName = "",
                repository = "test",
            ),
        )

        (result as GetApplicationRelease.Result.NewUpdate).release shouldBe GetApplicationRelease.Result.NewUpdate(
            release,
        ).release
    }

    @Test
    fun `When has update expect new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "v2.0.0",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
            ),
        )

        (result as GetApplicationRelease.Result.NewUpdate).release shouldBe GetApplicationRelease.Result.NewUpdate(
            release,
        ).release
    }

    @Test
    fun `When has no update expect no new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "v1.0.0",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v2.0.0",
                repository = "test",
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `When now is before three days expect no new update`() = runTest {
        every { preference.get() } returns Instant.now().toEpochMilli()
        every { preference.set(any()) }.answers { }

        val release = Release(
            "v1.0.0",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v2.0.0",
                repository = "test",
            ),
        )

        coVerify(exactly = 0) { releaseService.latest(any()) }
        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `When release quality is STABLE and includePrerelease false expect new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "v2.0.0",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
            quality = ReleaseQuality.STABLE,
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
                forceCheck = false,
                includePrerelease = false,
            ),
        )

        (result as GetApplicationRelease.Result.NewUpdate).release shouldBe release
    }

    @Test
    fun `When release quality is PRERELEASE and includePrerelease false expect no new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "v2.0.0-rc1",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
            quality = ReleaseQuality.PRERELEASE,
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
                forceCheck = false,
                includePrerelease = false,
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `When release quality is PRERELEASE and includePrerelease true expect new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "v2.0.0-rc1",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
            quality = ReleaseQuality.PRERELEASE,
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
                forceCheck = false,
                includePrerelease = true,
            ),
        )

        (result as GetApplicationRelease.Result.NewUpdate).release shouldBe release
    }

    @Test
    fun `When release quality is DRAFT and includePrerelease true expect no new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "v2.0.0",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
            quality = ReleaseQuality.DRAFT,
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
                forceCheck = false,
                includePrerelease = true,
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `When release quality is DEPRECATED and includePrerelease false expect no new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        val release = Release(
            "v2.0.0",
            "info",
            "http://example.com/release_link",
            "http://example.com/release_link.apk",
            quality = ReleaseQuality.DEPRECATED,
        )

        coEvery { releaseService.latest(any()) } returns release

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
                forceCheck = false,
                includePrerelease = false,
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }

    @Test
    fun `When service returns null expect no new update`() = runTest {
        every { preference.get() } returns 0
        every { preference.set(any()) }.answers { }

        coEvery { releaseService.latest(any()) } returns null

        val result = getApplicationRelease.await(
            GetApplicationRelease.Arguments(
                isPreview = false,
                commitCount = 0,
                versionName = "v1.0.0",
                repository = "test",
                forceCheck = false,
                includePrerelease = false,
            ),
        )

        result shouldBe GetApplicationRelease.Result.NoNewUpdate
    }
}
