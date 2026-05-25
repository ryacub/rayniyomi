package eu.kanade.tachiyomi.data.track.shikimori

import eu.kanade.tachiyomi.data.track.shikimori.dto.SMGraphQLData
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMGraphQLEntry
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMGraphQLError
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMGraphQLResponse
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUserRate
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUserRateMedia
import eu.kanade.tachiyomi.data.track.shikimori.dto.requireData
import eu.kanade.tachiyomi.data.track.shikimori.dto.shikimoriAnimeSearchPayload
import eu.kanade.tachiyomi.data.track.shikimori.dto.shikimoriMangaByIdPayload
import eu.kanade.tachiyomi.data.track.shikimori.dto.shikimoriMangaSearchPayload
import eu.kanade.tachiyomi.data.track.shikimori.dto.shikimoriUserRatesQueryPayload
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ShikimoriApi GraphQL migration")
class ShikimoriApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Nested
    @DisplayName("Feature 1: GraphQL payload builders")
    inner class GraphQLPayloadBuilders {

        @Test
        @DisplayName("shikimoriUserRatesQueryPayload produces valid GraphQL query JSON")
        fun queryPayloadStructure() {
            val payload = shikimoriUserRatesQueryPayload(
                userId = 111L,
                targetType = "Manga",
            )
            val root = json.parseToJsonElement(payload).jsonObject

            root["query"]!!.jsonPrimitive.content shouldContain "userRates"
            root["query"]!!.jsonPrimitive.content shouldContain "UserRateTargetTypeEnum"
            root["query"]!!.jsonPrimitive.content shouldContain "anime"
            root["query"]!!.jsonPrimitive.content shouldContain "manga"
            root["query"]!!.jsonPrimitive.content shouldContain "limit: 50"
            val vars = root["variables"]!!.jsonObject
            vars["userId"]!!.jsonPrimitive.content shouldBe "111"
            vars["targetType"]!!.jsonPrimitive.content shouldBe "Manga"
            vars.containsKey("targetId") shouldBe false
        }

        @Test
        @DisplayName("shikimoriUserRatesQueryPayload sends userId as string (ID type)")
        fun queryPayloadUserIdIsString() {
            val payload = shikimoriUserRatesQueryPayload(userId = 42L, targetType = "Anime")
            val root = json.parseToJsonElement(payload).jsonObject
            root["variables"]!!.jsonObject["userId"]!!.jsonPrimitive.content shouldBe "42"
        }

        @Test
        @DisplayName("shikimoriMangaSearchPayload uses search variable")
        fun mangaSearchPayloadUsesQueryVar() {
            val payload = shikimoriMangaSearchPayload("One Piece")
            payload shouldContain "One Piece"
            payload shouldContain "mangas"
            payload shouldContain "popularity"
        }

        @Test
        @DisplayName("shikimoriAnimeSearchPayload uses episodes field not chapters")
        fun animeSearchPayloadUsesEpisodes() {
            val payload = shikimoriAnimeSearchPayload("Naruto")
            payload shouldContain "episodes"
            payload shouldNotContain "\"chapters\""
        }

        @Test
        @DisplayName("shikimoriMangaByIdPayload sends id variable")
        fun mangaByIdPayloadSendsId() {
            val payload = shikimoriMangaByIdPayload(42L)
            val root = json.parseToJsonElement(payload).jsonObject
            root["variables"]!!.jsonObject["id"]!!.jsonPrimitive.content shouldBe "42"
        }
    }

    @Nested
    @DisplayName("Feature 2-4 & 7: SMUserRate mapping (manga + anime)")
    inner class UserRateMapping {

        private val mangaEntry = SMGraphQLEntry(
            id = "11",
            name = "Berserk",
            url = "/manga/11",
            score = 9.0,
            status = "ongoing",
            kind = "manga",
            chapters = 364L,
        )

        private val animeEntry = SMGraphQLEntry(
            id = "20",
            name = "Cowboy Bebop",
            url = "/anime/20",
            score = 8.5,
            status = "released",
            kind = "tv",
            episodes = 26L,
        )

        @Test
        @DisplayName("SMUserRate.toMangaTrack maps all fields correctly")
        fun smUserRateToMangaTrack() {
            val rate = SMUserRate(id = 555L, chapters = 10L, score = 8L, status = "watching")
            val track = rate.toMangaTrack(trackId = 4L, mediaId = 11L, manga = mangaEntry)

            track.library_id shouldBe 555L
            track.remote_id shouldBe 11L
            track.last_chapter_read shouldBe 10.0
            track.score shouldBe 8.0
            track.title shouldBe "Berserk"
            track.total_chapters shouldBe 364L
        }

        @Test
        @DisplayName("SMUserRate.toMangaTrack uses mediaId as fallback when manga is null")
        fun smUserRateToMangaTrackNullMedia() {
            val rate = SMUserRate(id = 555L, chapters = 5L, score = 0L, status = "planned")
            val track = rate.toMangaTrack(trackId = 4L, mediaId = 99L, manga = null)

            track.remote_id shouldBe 99L
            track.library_id shouldBe 555L
            track.total_chapters shouldBe 0L
        }

        @Test
        @DisplayName("SMUserRate.toMangaTrack maps null chapters to 0")
        fun smUserRateNullChaptersDefaultsToZero() {
            val rate = SMUserRate(id = 1L, chapters = null, score = 0L, status = "planned")
            val track = rate.toMangaTrack(trackId = 4L, mediaId = 11L, manga = null)
            track.last_chapter_read shouldBe 0.0
        }

        @Test
        @DisplayName("SMUserRate.toAnimeTrack maps all fields correctly")
        fun smUserRateToAnimeTrack() {
            val rate = SMUserRate(id = 888L, episodes = 24L, score = 9L, status = "completed")
            val track = rate.toAnimeTrack(trackId = 4L, mediaId = 20L, anime = animeEntry)

            track.library_id shouldBe 888L
            track.remote_id shouldBe 20L
            track.last_episode_seen shouldBe 24.0
            track.score shouldBe 9.0
            track.title shouldBe "Cowboy Bebop"
            track.total_episodes shouldBe 26L
        }

        @Test
        @DisplayName("SMUserRate.toAnimeTrack uses mediaId as fallback when anime is null")
        fun smUserRateToAnimeTrackNullMedia() {
            val rate = SMUserRate(id = 888L, episodes = 0L, score = 0L, status = "planned")
            val track = rate.toAnimeTrack(trackId = 4L, mediaId = 77L, anime = null)

            track.remote_id shouldBe 77L
            track.library_id shouldBe 888L
            track.total_episodes shouldBe 0L
        }

        @Test
        @DisplayName("SMUserRate.toAnimeTrack maps null episodes to 0")
        fun smUserRateNullEpisodesDefaultsToZero() {
            val rate = SMUserRate(id = 1L, episodes = null, score = 0L, status = "planned")
            val track = rate.toAnimeTrack(trackId = 4L, mediaId = 20L, anime = null)
            track.last_episode_seen shouldBe 0.0
        }

        @Test
        @DisplayName("tracking_url is absolute when SMGraphQLEntry url is relative")
        fun trackingUrlPrependsBaseUrl() {
            val rate = SMUserRate(id = 1L, score = 0L, status = "planned")
            val track = rate.toMangaTrack(trackId = 4L, mediaId = 11L, manga = mangaEntry)
            track.tracking_url shouldBe "${ShikimoriApi.BASE_URL}/manga/11"
        }

        @Test
        @DisplayName("tracking_url is unchanged when SMGraphQLEntry url is absolute")
        fun trackingUrlAbsoluteUrlIsUnchanged() {
            val entry = mangaEntry.copy(url = "https://shikimori.one/manga/11")
            val rate = SMUserRate(id = 1L, score = 0L, status = "planned")
            val track = rate.toMangaTrack(trackId = 4L, mediaId = 11L, manga = entry)
            track.tracking_url shouldBe "https://shikimori.one/manga/11"
        }

        @Test
        @DisplayName("SMUserRate.manga.id is used for client-side filtering")
        fun smUserRateMangaIdForFiltering() {
            val rate = SMUserRate(id = 1L, score = 0L, status = "planned", manga = SMUserRateMedia(id = "11"))
            rate.manga?.id shouldBe "11"
        }

        @Test
        @DisplayName("SMUserRate.anime.id is used for client-side filtering")
        fun smUserRateAnimeIdForFiltering() {
            val rate = SMUserRate(id = 1L, score = 0L, status = "planned", anime = SMUserRateMedia(id = "20"))
            rate.anime?.id shouldBe "20"
        }
    }

    @Nested
    @DisplayName("Feature 1: requireData error handling")
    inner class RequireData {

        @Test
        @DisplayName("requireData returns data when present")
        fun requireDataReturnsData() {
            val data = SMGraphQLData()
            val response = SMGraphQLResponse(data = data)
            response.requireData() shouldBe data
        }

        @Test
        @DisplayName("requireData throws when data is null and errors are present")
        fun requireDataThrowsOnErrors() {
            val response = SMGraphQLResponse(
                data = null,
                errors = listOf(SMGraphQLError("rate limit exceeded")),
            )
            val ex = shouldThrow<IllegalStateException> { response.requireData() }
            ex.message shouldContain "rate limit exceeded"
        }

        @Test
        @DisplayName("requireData throws with 'empty response' when data null and no errors")
        fun requireDataThrowsEmptyResponse() {
            val response = SMGraphQLResponse(data = null, errors = emptyList())
            val ex = shouldThrow<IllegalStateException> { response.requireData() }
            ex.message shouldContain "empty response"
        }
    }

    @Nested
    @DisplayName("Feature 8: Dead code removed")
    inner class DeadCodeRemoval {

        @Test
        @DisplayName("SMUserListEntry class does not exist")
        fun smUserListEntryRemoved() {
            try {
                Class.forName("eu.kanade.tachiyomi.data.track.shikimori.dto.SMUserListEntry")
                throw AssertionError("SMUserListEntry should be deleted but was found")
            } catch (e: ClassNotFoundException) {
                // Expected
            }
        }
    }
}
