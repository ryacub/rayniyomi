package eu.kanade.tachiyomi.data.track.shikimori

import android.net.Uri
import androidx.annotation.VisibleForTesting
import androidx.core.net.toUri
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMAddEntryResponse
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMGraphQLResponse
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMOAuth
import eu.kanade.tachiyomi.data.track.shikimori.dto.SMUser
import eu.kanade.tachiyomi.data.track.shikimori.dto.requireData
import eu.kanade.tachiyomi.data.track.shikimori.dto.shikimoriAnimeByIdPayload
import eu.kanade.tachiyomi.data.track.shikimori.dto.shikimoriAnimeSearchPayload
import eu.kanade.tachiyomi.data.track.shikimori.dto.shikimoriMangaByIdPayload
import eu.kanade.tachiyomi.data.track.shikimori.dto.shikimoriMangaSearchPayload
import eu.kanade.tachiyomi.data.track.shikimori.dto.shikimoriUserRatesQueryPayload
import eu.kanade.tachiyomi.network.DELETE
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.network.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy
import tachiyomi.domain.track.anime.model.AnimeTrack as DomainAnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack as DomainMangaTrack

class ShikimoriApi(
    private val trackId: Long,
    private val client: OkHttpClient,
    interceptor: ShikimoriInterceptor,
) {

    private val json: Json by injectLazy()

    private val authClient = client.newBuilder().addInterceptor(interceptor).build()

    suspend fun addLibManga(track: MangaTrack, userId: String): MangaTrack {
        return withIOContext {
            with(json) {
                val payload = buildJsonObject {
                    putJsonObject("user_rate") {
                        put("user_id", userId)
                        put("target_id", track.remote_id)
                        put("target_type", "Manga")
                        put("chapters", track.last_chapter_read.toInt())
                        put("score", track.score.toInt())
                        put("status", track.toShikimoriStatus())
                    }
                }
                authClient.newCall(
                    POST(
                        "$API_URL/v2/user_rates",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                ).awaitSuccess()
                    .parseAs<SMAddEntryResponse>()
                    .let { track.library_id = it.id }
                track
            }
        }
    }

    suspend fun updateLibManga(track: MangaTrack, userId: String): MangaTrack = addLibManga(
        track,
        userId,
    )

    suspend fun deleteLibManga(track: DomainMangaTrack) {
        withIOContext {
            authClient
                .newCall(DELETE("$API_URL/v2/user_rates/${track.libraryId}"))
                .awaitSuccess()
        }
    }

    suspend fun addLibAnime(track: AnimeTrack, userId: String): AnimeTrack {
        return withIOContext {
            with(json) {
                val payload = buildJsonObject {
                    putJsonObject("user_rate") {
                        put("user_id", userId)
                        put("target_id", track.remote_id)
                        put("target_type", "Anime")
                        put("episodes", track.last_episode_seen.toInt())
                        put("score", track.score.toInt())
                        put("status", track.toShikimoriStatus())
                    }
                }
                authClient.newCall(
                    POST(
                        "$API_URL/v2/user_rates",
                        body = payload.toString().toRequestBody(jsonMime),
                    ),
                ).awaitSuccess()
                    .parseAs<SMAddEntryResponse>()
                    .let { track.library_id = it.id }
                track
            }
        }
    }

    suspend fun updateLibAnime(track: AnimeTrack, userId: String): AnimeTrack = addLibAnime(
        track,
        userId,
    )

    suspend fun deleteLibAnime(track: DomainAnimeTrack) {
        withIOContext {
            authClient
                .newCall(DELETE("$API_URL/v2/user_rates/${track.libraryId}"))
                .awaitSuccess()
        }
    }

    suspend fun search(search: String): List<MangaTrackSearch> {
        return withIOContext {
            with(json) {
                authClient.newCall(graphQLRequest(shikimoriMangaSearchPayload(search)))
                    .awaitSuccess()
                    .parseAs<SMGraphQLResponse>()
                    .requireData()
                    .mangas
                    .orEmpty()
                    .mapNotNull { it.toMangaTrack(trackId) }
            }
        }
    }

    suspend fun searchAnime(search: String): List<AnimeTrackSearch> {
        return withIOContext {
            with(json) {
                authClient.newCall(graphQLRequest(shikimoriAnimeSearchPayload(search)))
                    .awaitSuccess()
                    .parseAs<SMGraphQLResponse>()
                    .requireData()
                    .animes
                    .orEmpty()
                    .mapNotNull { it.toAnimeTrack(trackId) }
            }
        }
    }

    suspend fun findLibManga(track: MangaTrack, userId: String): MangaTrack? {
        return withIOContext {
            val manga = with(json) {
                authClient.newCall(graphQLRequest(shikimoriMangaByIdPayload(track.remote_id)))
                    .awaitSuccess()
                    .parseAs<SMGraphQLResponse>()
                    .requireData()
                    .mangas
                    .orEmpty()
                    .firstOrNull()
            }

            with(json) {
                val payload = shikimoriUserRatesQueryPayload(
                    userId = userId.toLong(),
                    targetType = "Manga",
                )
                authClient.newCall(graphQLRequest(payload))
                    .awaitSuccess()
                    .parseAs<SMGraphQLResponse>()
                    .let { response ->
                        response.data?.userRates
                            .orEmpty()
                            .filter { it.manga?.id == track.remote_id.toString() }
                            .map { it.toMangaTrack(trackId, track.remote_id, manga) }
                            .firstOrNull()
                    }
            }
        }
    }

    suspend fun findLibAnime(track: AnimeTrack, userId: String): AnimeTrack? {
        return withIOContext {
            val anime = with(json) {
                authClient.newCall(graphQLRequest(shikimoriAnimeByIdPayload(track.remote_id)))
                    .awaitSuccess()
                    .parseAs<SMGraphQLResponse>()
                    .requireData()
                    .animes
                    .orEmpty()
                    .firstOrNull()
            }

            with(json) {
                val payload = shikimoriUserRatesQueryPayload(
                    userId = userId.toLong(),
                    targetType = "Anime",
                )
                authClient.newCall(graphQLRequest(payload))
                    .awaitSuccess()
                    .parseAs<SMGraphQLResponse>()
                    .let { response ->
                        response.data?.userRates
                            .orEmpty()
                            .filter { it.anime?.id == track.remote_id.toString() }
                            .map { it.toAnimeTrack(trackId, track.remote_id, anime) }
                            .firstOrNull()
                    }
            }
        }
    }

    suspend fun getCurrentUser(): Int {
        return with(json) {
            authClient.newCall(GET("$API_URL/users/whoami"))
                .awaitSuccess()
                .parseAs<SMUser>()
                .id
        }
    }

    suspend fun accessToken(code: String): SMOAuth {
        return withIOContext {
            with(json) {
                client.newCall(accessTokenRequest(code))
                    .awaitSuccess()
                    .parseAs()
            }
        }
    }

    private fun graphQLRequest(payload: String) = POST(
        GRAPHQL_URL,
        body = payload.toRequestBody(jsonMime),
    )

    companion object {
        const val BASE_URL = "https://shikimori.one"
        private const val API_URL = "$BASE_URL/api"
        private const val GRAPHQL_URL = "https://shikimori.io/api/graphql"
        private const val OAUTH_URL = "$BASE_URL/oauth/token"
        private const val LOGIN_URL = "$BASE_URL/oauth/authorize"

        private const val REDIRECT_URL = "rayniyomi://shikimori-auth"

        private const val CLIENT_ID = "SmkA50jJviGntLvJLsEwEVetogb0RnS35OgvFCQttpM"
        private const val CLIENT_SECRET = "lOFK6rLfV8Eu7cO0V9pMLIoC8X2f3BL11HVn-MRitvQ"

        fun authUrl(): Uri = authUrlString().toUri()

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal fun authUrlString(): String = LOGIN_URL.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", CLIENT_ID)
            .addQueryParameter("redirect_uri", REDIRECT_URL)
            .addQueryParameter("response_type", "code")
            .build()
            .toString()

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal fun accessTokenRequest(code: String) = POST(
            OAUTH_URL,
            body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("code", code)
                .add("redirect_uri", REDIRECT_URL)
                .build(),
        )

        fun refreshTokenRequest(token: String) = POST(
            OAUTH_URL,
            body = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", token)
                .build(),
        )
    }
}
