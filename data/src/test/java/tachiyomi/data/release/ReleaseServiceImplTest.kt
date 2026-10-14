package tachiyomi.data.release

import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import tachiyomi.domain.release.interactor.GetApplicationRelease
import java.time.Instant

class ReleaseServiceImplTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val arguments = GetApplicationRelease.Arguments(
        isPreview = false,
        commitCount = 0,
        versionName = "0.18.1.449",
        repository = "ryacub/rayniyomi",
    )

    @Test
    fun `parsePublishedAt returns null when value is null`() {
        ReleaseServiceImpl.parsePublishedAt(null).shouldBeNull()
    }

    @Test
    fun `parsePublishedAt returns null when value is malformed`() {
        ReleaseServiceImpl.parsePublishedAt("not-a-date").shouldBeNull()
    }

    @Test
    fun `parsePublishedAt returns epoch millis when value is valid ISO instant`() {
        val instant = "2026-03-27T05:20:04Z"

        ReleaseServiceImpl.parsePublishedAt(instant) shouldBe Instant.parse(instant).toEpochMilli()
    }

    @Test
    fun `latest skips a newer runtime release`() {
        val runtime = release("cloudstream-runtime-v16.1", "cloudstream-runtime-v16.1.apk", "SHA256SUMS.txt")
        val app = release("v0.18.1.448", "rayniyomi-v0.18.1.448.apk")

        val result = latest(latestRelease = runtime, releases = listOf(runtime, app))

        result.shouldNotBeNull().version shouldBe "v0.18.1.448"
    }

    @Test
    fun `latest skips drafts and prereleases`() {
        val draft = release("v0.18.1.450", "rayniyomi-v0.18.1.450.apk", draft = true)
        val prerelease = release("v0.18.1.449", "rayniyomi-v0.18.1.449.apk", prerelease = true)
        val app = release("v0.18.1.448", "rayniyomi-v0.18.1.448.apk")

        val result = latest(latestRelease = draft, releases = listOf(draft, prerelease, app))

        result.shouldNotBeNull().version shouldBe "v0.18.1.448"
    }

    @Test
    fun `latest returns null when no app release exists`() {
        val runtime = release("cloudstream-runtime-v16.1", "cloudstream-runtime-v16.1.apk", "SHA256SUMS.txt")
        val plugin = release("plugin-v1.2.0", "lightnovel-plugin-plugin-v1.2.0.apk", "SHA256SUMS.txt")

        latest(latestRelease = runtime, releases = listOf(runtime, plugin)).shouldBeNull()
    }

    @Test
    fun `latest never links the checksum file`() {
        val app = release("v0.18.1.448", "rayniyomi-v0.18.1.448.apk", "SHA256SUMS.txt")

        val result = latest(latestRelease = app, releases = listOf(app))

        result.shouldNotBeNull().downloadLink shouldBe downloadUrl("rayniyomi-v0.18.1.448.apk")
    }

    @Test
    fun `latest prefers the apk for the device ABI`() {
        val app = release(
            "v0.18.1.448",
            "rayniyomi-arm64-v8a-v0.18.1.448.apk",
            "rayniyomi-x86_64-v0.18.1.448.apk",
            "rayniyomi-v0.18.1.448.apk",
        )

        val result = latest(latestRelease = app, releases = listOf(app), primaryAbi = "x86_64")

        result.shouldNotBeNull().downloadLink shouldBe downloadUrl("rayniyomi-x86_64-v0.18.1.448.apk")
    }

    private fun latest(
        latestRelease: String,
        releases: List<String>,
        primaryAbi: String = "arm64-v8a",
    ) = runBlocking {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val body = when (chain.request().url.encodedPath) {
                    "/repos/ryacub/rayniyomi/releases/latest" -> latestRelease
                    "/repos/ryacub/rayniyomi/releases" -> releases.joinToString(prefix = "[", postfix = "]")
                    else -> error("Unexpected request ${chain.request().url}")
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody())
                    .build()
            }
            .build()
        val networkHelper = mockk<NetworkHelper> { every { client } returns httpClient }

        ReleaseServiceImpl(networkService = networkHelper, json = json, primaryAbi = primaryAbi)
            .latest(arguments)
    }

    private fun release(
        tag: String,
        vararg assetNames: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ): String {
        val assets = assetNames.joinToString { name ->
            """{"name":"$name","browser_download_url":"${downloadUrl(name)}"}"""
        }
        return """
            {
              "tag_name": "$tag",
              "body": "notes",
              "html_url": "https://github.com/ryacub/rayniyomi/releases/tag/$tag",
              "draft": $draft,
              "prerelease": $prerelease,
              "published_at": "2026-10-14T01:30:08Z",
              "assets": [$assets]
            }
        """.trimIndent()
    }

    private fun downloadUrl(name: String) = "https://github.com/ryacub/rayniyomi/releases/download/$name"
}
