package eu.kanade.tachiyomi.data.track.shikimori

import eu.kanade.tachiyomi.data.track.shikimori.dto.SMGraphQLEntry
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMGraphQLPoster
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMGraphQLResponse
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUserRate
import eu.kanade.tachiyomi.data.track.shikimori.dto.requireData
import eu.kanade.tachiyomi.data.track.shikimori.dto.shikimoriAnimeSearchPayload
import eu.kanade.tachiyomi.data.track.shikimori.dto.shikimoriMangaSearchPayload
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ShikimoriMappingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `GraphQL manga search maps nullable chapters safely`() {
        val response = json.decodeFromString<SMGraphQLResponse>(
            """
            {
              "data": {
                "mangas": [{
                  "id": "11",
                  "name": "Naruto",
                  "url": "https://shikimori.io/mangas/z11-naruto",
                  "score": 8.08,
                  "status": "released",
                  "kind": "manga",
                  "chapters": null,
                  "poster": { "mainUrl": "https://img.example/main.webp" },
                  "airedOn": { "date": "1999-09-21" }
                }]
              }
            }
            """.trimIndent(),
        )

        val track = response.data!!.mangas.single().toMangaTrack(trackId = 4L)!!

        track.remote_id shouldBe 11L
        track.title shouldBe "Naruto"
        track.total_chapters shouldBe 0L
        track.cover_url shouldBe "https://img.example/main.webp"
        track.tracking_url shouldBe "https://shikimori.io/mangas/z11-naruto"
        track.publishing_status shouldBe "released"
        track.publishing_type shouldBe "manga"
        track.start_date shouldBe "1999-09-21"
    }

    @Test
    fun `GraphQL anime search maps nullable episodes safely`() {
        val response = json.decodeFromString<SMGraphQLResponse>(
            """
            {
              "data": {
                "animes": [{
                  "id": "20",
                  "name": "Naruto",
                  "url": "/animes/z20-naruto",
                  "score": 8.02,
                  "status": "released",
                  "kind": "tv",
                  "episodes": null,
                  "poster": { "originalUrl": "https://img.example/original.jpg" },
                  "airedOn": { "date": "2002-10-03" }
                }]
              }
            }
            """.trimIndent(),
        )

        val track = response.data!!.animes.single().toAnimeTrack(trackId = 4L)!!

        track.remote_id shouldBe 20L
        track.title shouldBe "Naruto"
        track.total_episodes shouldBe 0L
        track.cover_url shouldBe "https://img.example/original.jpg"
        track.tracking_url shouldBe "https://shikimori.one/animes/z20-naruto"
        track.publishing_status shouldBe "released"
        track.publishing_type shouldBe "tv"
        track.start_date shouldBe "2002-10-03"
    }

    @Test
    fun `user rate mapping keeps media id and user rate id distinct`() {
        val manga = SMGraphQLEntry(
            id = "11",
            name = "Naruto",
            url = "https://shikimori.io/mangas/z11-naruto",
            chapters = 700,
            poster = SMGraphQLPoster(mainUrl = "https://img.example/main.webp"),
        )
        val userRate = SMUserRate(
            id = 555L,
            chapters = 12L,
            episodes = 0L,
            score = 9L,
            status = "watching",
        )

        val track = userRate.toMangaTrack(trackId = 4L, mediaId = 11L, manga = manga)

        track.remote_id shouldBe 11L
        track.library_id shouldBe 555L
        track.last_chapter_read shouldBe 12.0
        track.total_chapters shouldBe 700L
        track.status shouldBe Shikimori.READING
    }

    @Test
    fun `status conversion covers known Shikimori statuses`() {
        toTrackStatus("watching") shouldBe Shikimori.READING
        toTrackStatus("completed") shouldBe Shikimori.COMPLETED
        toTrackStatus("on_hold") shouldBe Shikimori.ON_HOLD
        toTrackStatus("dropped") shouldBe Shikimori.DROPPED
        toTrackStatus("planned") shouldBe Shikimori.PLAN_TO_READ
        toTrackStatus("rewatching") shouldBe Shikimori.REREADING
    }

    @Test
    fun `GraphQL search payloads target expected media fields`() {
        shikimoriMangaSearchPayload("naruto").let { payload ->
            payload.contains("mangas(search: \$query") shouldBe true
            payload.contains("\"query\":\"naruto\"") shouldBe true
            payload.contains("chapters") shouldBe true
        }

        shikimoriAnimeSearchPayload("naruto").let { payload ->
            payload.contains("animes(search: \$query") shouldBe true
            payload.contains("\"query\":\"naruto\"") shouldBe true
            payload.contains("episodes") shouldBe true
        }
    }

    @Test
    fun `GraphQL error-only response throws with remote message`() {
        val response = json.decodeFromString<SMGraphQLResponse>(
            """
            {
              "errors": [{
                "message": "Field 'unknown' doesn't exist on type 'Query'"
              }]
            }
            """.trimIndent(),
        )

        val exception = assertThrows(IllegalStateException::class.java) {
            response.requireData()
        }

        exception.message shouldBe
            "Shikimori GraphQL response did not include data: Field 'unknown' doesn't exist on type 'Query'"
    }
}
