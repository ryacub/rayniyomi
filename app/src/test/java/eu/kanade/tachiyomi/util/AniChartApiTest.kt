package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.data.track.simkl.Simkl
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackItem
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.track.anime.model.AnimeTrack
import java.time.OffsetDateTime

class AniChartApiTest {
    private val previousGraph = AppGraphHolder.graphOrNull
    private val early = "2999-01-01T00:00:00Z"
    private val body = """
        [
          {"date":"2000-01-01T00:00:00Z","ids":{"simkl_id":7,"mal":16498},"episode":{"episode":1}},
          {"date":"2000-01-01T00:00:00Z","ids":{"simkl_id":42,"mal":null},"episode":{"episode":1}},
          {"date":"2999-01-02T00:00:00Z","ids":{"simkl_id":42,"mal":null},"episode":{"episode":3}},
          {"date":"$early","ids":{"simkl_id":42,"mal":null},"episode":{"episode":2}}
        ]
    """.trimIndent().replace(": ", ":")

    @AfterEach
    fun tearDown() {
        unmockkConstructor(OkHttpClient::class)
        AppGraphHolder.graphOrNull = previousGraph
    }

    @Test
    fun `Simkl chooses the earliest future matching episode`() = runTest {
        val client = calendarClient()
        AppGraphHolder.graph = mockk<AppGraph> { every { networkHelper.client } returns client }
        val api = AniChartApi()
        AniChartApi::class.java.getDeclaredField("client").apply {
            isAccessible = true
            set(api, client)
        }

        api.loadAiringTime(Anime.create(), listOf(simklTrack()), manualFetch = true) shouldBe
            Pair(2, OffsetDateTime.parse(early).toEpochSecond())
    }

    @Test
    fun `airing requests use the configured network client`() = runTest {
        val client = calendarClient()
        AppGraphHolder.graph = mockk<AppGraph> { every { networkHelper.client } returns client }
        mockkConstructor(OkHttpClient::class)
        every { anyConstructed<OkHttpClient>().newCall(any()) } throws
            AssertionError("AniChart must use the configured NetworkHelper client")

        AniChartApi().loadAiringTime(Anime.create(), listOf(simklTrack()), manualFetch = true) shouldBe
            Pair(2, OffsetDateTime.parse(early).toEpochSecond())
    }

    private fun simklTrack(): AnimeTrackItem {
        val track = mockk<AnimeTrack> { every { remoteId } returns 42L }
        return AnimeTrackItem(track, mockk<Simkl>())
    }

    private fun calendarClient(): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody())
            .build()
    }.build()
}
