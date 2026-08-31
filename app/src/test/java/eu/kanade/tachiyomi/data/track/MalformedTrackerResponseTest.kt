package eu.kanade.tachiyomi.data.track

import eu.kanade.tachiyomi.data.track.anilist.dto.ALSearchItem
import eu.kanade.tachiyomi.data.track.jellyfin.JellyfinApi
import eu.kanade.tachiyomi.data.track.jellyfin.dto.JFItemList
import eu.kanade.tachiyomi.data.track.jellyfin.requireIndexNumber
import eu.kanade.tachiyomi.data.track.kavita.ChapterDto
import eu.kanade.tachiyomi.data.track.kavita.Kavita
import eu.kanade.tachiyomi.data.track.kavita.KavitaApi
import eu.kanade.tachiyomi.data.track.kavita.KavitaInterceptor
import eu.kanade.tachiyomi.data.track.kavita.OAuth
import eu.kanade.tachiyomi.data.track.kavita.requireNumber
import eu.kanade.tachiyomi.data.track.simkl.dto.SimklSyncResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

class MalformedTrackerResponseTest {

    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        Injekt.addSingleton<Json>(json)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Jellyfin response without an episode number reports a malformed response`() {
        val response = json.decodeFromString<JFItemList>(
            """
            {
              "Items": [{
                "Name": "Episode",
                "Id": "episode-id",
                "UserData": { "Played": false }
              }]
            }
            """.trimIndent(),
        )

        val exception = assertThrows(MalformedTrackerResponseException::class.java) {
            response.items.single().requireIndexNumber()
        }

        assertEquals(
            "Jellyfin returned an invalid response: episode index number is absent",
            exception.message,
        )
    }

    @Test
    fun `Jellyfin API reports a malformed response when an episode number is absent`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"Name":"Series","Id":"series-id","UserData":{"Played":false}}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """{"Items":[{"Name":"Episode","Id":"episode-id","UserData":{"Played":false}}]}""",
            ),
        )
        val url = server.url("/Items/series-id")
            .newBuilder()
            .fragment("seriesId,show-id,season-id")
            .build()

        val exception = try {
            JellyfinApi(trackId = 1L, client = OkHttpClient()).getTrackSearch(url.toString())
            null
        } catch (e: MalformedTrackerResponseException) {
            e
        }

        assertEquals(
            "Jellyfin returned an invalid response: episode index number is absent",
            exception?.message,
        )
    }

    @Test
    fun `AniList response without manga staff reports a malformed response`() {
        val response = json.decodeFromString<ALSearchItem>(
            """
            {
              "id": 1,
              "title": { "userPreferred": "Manga" },
              "coverImage": { "large": "https://example.com/cover.jpg" },
              "description": null,
              "format": null,
              "status": null,
              "startDate": { "year": 2020, "month": 1, "day": 1 },
              "chapters": null,
              "episodes": null,
              "averageScore": null,
              "studios": null
            }
            """.trimIndent(),
        )

        val exception = assertThrows(MalformedTrackerResponseException::class.java) {
            response.toALManga()
        }

        assertEquals(
            "AniList returned an invalid response: manga staff is absent",
            exception.message,
        )
    }

    @Test
    fun `Kavita response without a chapter number reports a malformed response`() {
        val response = json.decodeFromString<ChapterDto>("{}")

        val exception = assertThrows(MalformedTrackerResponseException::class.java) {
            response.requireNumber()
        }

        assertEquals(
            "Kavita returned an invalid response: chapter number is absent",
            exception.message,
        )
    }

    @Test
    fun `Kavita API reports a malformed response when a chapter number is absent`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"id":1,"name":"Series","pages":1,"pagesRead":0,"format":1,"libraryId":1}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """[{"id":1,"number":1,"name":"Volume","pages":1,"pagesRead":0,"lastModified":"","created":"","seriesId":1,"chapters":[{}]}]""",
            ),
        )
        val kavita = mockk<Kavita>()
        every { kavita.authentications } returns OAuth(emptyList())
        every { kavita.api } returns mockk(relaxed = true)
        val interceptor = KavitaInterceptor(kavita)
        val exception = try {
            KavitaApi(OkHttpClient(), interceptor).getTrackSearch(server.url("/api/Series/1").toString())
            null
        } catch (e: MalformedTrackerResponseException) {
            e
        }

        assertEquals(
            "Kavita returned an invalid response: chapter number is absent",
            exception?.message,
        )
    }

    @Test
    fun `Simkl response without a total episode count reports a malformed response`() {
        val response = json.decodeFromString<SimklSyncResult>(
            """
            {
              "anime": [{
                "show": { "title": "Show", "ids": { "simkl": 123 } }
              }]
            }
            """.trimIndent(),
        )

        val exception = assertThrows(MalformedTrackerResponseException::class.java) {
            response.anime!!.single().toAnimeTrack("show", "tv", "watching")
        }

        assertEquals(
            "Simkl returned an invalid response: total episode count is absent",
            exception.message,
        )
    }
}
