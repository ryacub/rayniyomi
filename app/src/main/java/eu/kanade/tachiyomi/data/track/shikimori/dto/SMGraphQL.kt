package eu.kanade.tachiyomi.data.track.shikimori.dto

import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.data.track.shikimori.toTrackStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack as DbAnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack as DbMangaTrack

@Serializable
internal data class SMGraphQLResponse(
    val data: SMGraphQLData? = null,
    val errors: List<SMGraphQLError> = emptyList(),
)

@Serializable
internal data class SMGraphQLData(
    val mangas: List<SMGraphQLEntry> = emptyList(),
    val animes: List<SMGraphQLEntry> = emptyList(),
    val userRateCreate: SMUserRateResponse? = null,
    val userRateUpdate: SMUserRateResponse? = null,
    val userRateDelete: SMUserRateResponse? = null,
    val userRates: List<SMUserRate> = emptyList(),
)

@Serializable
internal data class SMGraphQLError(
    val message: String,
)

@Serializable
internal data class SMGraphQLEntry(
    val id: String,
    val name: String,
    val url: String,
    val score: Double = 0.0,
    val status: String = "",
    val kind: String = "",
    val chapters: Long? = null,
    val episodes: Long? = null,
    val poster: SMGraphQLPoster? = null,
    val airedOn: SMGraphQLDate? = null,
) {
    fun toMangaTrack(trackId: Long): MangaTrackSearch? {
        val mediaId = id.toLongOrNull() ?: return null
        return MangaTrackSearch.create(trackId).apply {
            remote_id = mediaId
            title = name
            total_chapters = chapters ?: 0L
            cover_url = poster.toCoverUrl()
            summary = ""
            score = this@SMGraphQLEntry.score
            tracking_url = url.toShikimoriUrl()
            publishing_status = this@SMGraphQLEntry.status
            publishing_type = this@SMGraphQLEntry.kind
            start_date = airedOn?.date.orEmpty()
        }
    }

    fun toAnimeTrack(trackId: Long): AnimeTrackSearch? {
        val mediaId = id.toLongOrNull() ?: return null
        return AnimeTrackSearch.create(trackId).apply {
            remote_id = mediaId
            title = name
            total_episodes = episodes ?: 0L
            cover_url = poster.toCoverUrl()
            summary = ""
            score = this@SMGraphQLEntry.score
            tracking_url = url.toShikimoriUrl()
            publishing_status = this@SMGraphQLEntry.status
            publishing_type = this@SMGraphQLEntry.kind
            start_date = airedOn?.date.orEmpty()
        }
    }
}

@Serializable
internal data class SMGraphQLPoster(
    val mainUrl: String? = null,
    val originalUrl: String? = null,
)

@Serializable
internal data class SMGraphQLDate(
    val date: String? = null,
)

@Serializable
internal data class SMUserRateResponse(
    val id: Long,
    val chapters: Long? = null,
    val episodes: Long? = null,
    val score: Long = 0,
    val status: String = "",
)

@Serializable
internal data class SMUserRate(
    val id: Long,
    val chapters: Long? = null,
    val episodes: Long? = null,
    val score: Long = 0,
    val status: String = "",
) {
    internal fun toMangaTrack(trackId: Long, mediaId: Long, manga: SMGraphQLEntry?): DbMangaTrack {
        val searchTrack = manga?.toMangaTrack(trackId)
        return DbMangaTrack.create(trackId).apply {
            title = searchTrack?.title.orEmpty()
            remote_id = searchTrack?.remote_id ?: mediaId
            total_chapters = searchTrack?.total_chapters ?: 0L
            library_id = this@SMUserRate.id
            last_chapter_read = chapters?.toDouble() ?: 0.0
            score = this@SMUserRate.score.toDouble()
            status = toTrackStatus(this@SMUserRate.status)
            tracking_url = searchTrack?.tracking_url ?: ShikimoriApi.BASE_URL
        }
    }

    internal fun toAnimeTrack(trackId: Long, mediaId: Long, anime: SMGraphQLEntry?): DbAnimeTrack {
        val searchTrack = anime?.toAnimeTrack(trackId)
        return DbAnimeTrack.create(trackId).apply {
            title = searchTrack?.title.orEmpty()
            remote_id = searchTrack?.remote_id ?: mediaId
            total_episodes = searchTrack?.total_episodes ?: 0L
            library_id = this@SMUserRate.id
            last_episode_seen = episodes?.toDouble() ?: 0.0
            score = this@SMUserRate.score.toDouble()
            status = toTrackStatus(this@SMUserRate.status)
            tracking_url = searchTrack?.tracking_url ?: ShikimoriApi.BASE_URL
        }
    }
}

internal fun shikimoriMangaSearchPayload(query: String): String {
    return shikimoriGraphQLPayload(
        """
        |query SearchManga(${'$'}query: String!) {
            |mangas(search: ${'$'}query, order: popularity, limit: 20) {
                |id
                |name
                |url
                |score
                |status
                |kind
                |chapters
                |poster {
                    |mainUrl
                    |originalUrl
                |}
                |airedOn {
                    |date
                |}
            |}
        |}
        """.trimMargin(),
        query,
    )
}

internal fun shikimoriAnimeSearchPayload(query: String): String {
    return shikimoriGraphQLPayload(
        """
        |query SearchAnime(${'$'}query: String!) {
            |animes(search: ${'$'}query, order: popularity, limit: 20) {
                |id
                |name
                |url
                |score
                |status
                |kind
                |episodes
                |poster {
                    |mainUrl
                    |originalUrl
                |}
                |airedOn {
                    |date
                |}
            |}
        |}
        """.trimMargin(),
        query,
    )
}

internal fun shikimoriMangaByIdPayload(id: Long): String {
    return shikimoriGraphQLPayload(
        """
        |query MangaById(${'$'}id: String!) {
            |mangas(ids: ${'$'}id, limit: 1) {
                |id
                |name
                |url
                |score
                |status
                |kind
                |chapters
                |poster {
                    |mainUrl
                    |originalUrl
                |}
                |airedOn {
                    |date
                |}
            |}
        |}
        """.trimMargin(),
        id.toString(),
    )
}

internal fun shikimoriAnimeByIdPayload(id: Long): String {
    return shikimoriGraphQLPayload(
        """
        |query AnimeById(${'$'}id: String!) {
            |animes(ids: ${'$'}id, limit: 1) {
                |id
                |name
                |url
                |score
                |status
                |kind
                |episodes
                |poster {
                    |mainUrl
                    |originalUrl
                |}
                |airedOn {
                    |date
                |}
            |}
        |}
        """.trimMargin(),
        id.toString(),
    )
}

internal fun shikimoriUserRateCreatePayload(
    userId: Long,
    targetId: Long,
    targetType: String,
    chapters: Long? = null,
    episodes: Long? = null,
    score: Long = 0,
    status: String,
): String {
    return shikimoriGraphQLMutationPayload(
        """
        |mutation CreateUserRate(${'$'}userId: Int!, ${'$'}targetId: Int!, ${'$'}targetType: String!, ${'$'}chapters: Int, ${'$'}episodes: Int, ${'$'}score: Int, ${'$'}status: String!) {
            |userRateCreate(input: {userId: ${'$'}userId, targetId: ${'$'}targetId, targetType: ${'$'}targetType, chapters: ${'$'}chapters, episodes: ${'$'}episodes, score: ${'$'}score, status: ${'$'}status}) {
                |id
                |chapters
                |episodes
                |score
                |status
            |}
        |}
        """.trimMargin(),
        userId,
        targetId,
        targetType,
        chapters,
        episodes,
        score,
        status,
    )
}

internal fun shikimoriUserRateDeletePayload(id: Long): String {
    return buildJsonObject {
        put(
            "query",
            """
            |mutation DeleteUserRate(${'$'}id: Int!) {
                |userRateDelete(id: ${'$'}id) {
                    |id
                |}
            |}
            """.trimMargin(),
        )
        putJsonObject("variables") {
            put("id", id)
        }
    }.toString()
}

internal fun shikimoriUserRatesQueryPayload(
    userId: Long,
    targetId: Long,
    targetType: String,
): String {
    return buildJsonObject {
        put(
            "query",
            """
            |query UserRates(${'$'}userId: Int!, ${'$'}targetId: Int!, ${'$'}targetType: String!) {
                |userRates(userId: ${'$'}userId, targetId: ${'$'}targetId, targetType: ${'$'}targetType) {
                    |id
                    |chapters
                    |episodes
                    |score
                    |status
                |}
            |}
            """.trimMargin(),
        )
        putJsonObject("variables") {
            put("userId", userId)
            put("targetId", targetId)
            put("targetType", targetType)
        }
    }.toString()
}

internal fun SMGraphQLResponse.requireData(): SMGraphQLData {
    data?.let { return it }
    val errorMessage = errors.joinToString("; ") { it.message }.ifBlank { "empty response" }
    throw IllegalStateException("Shikimori GraphQL response did not include data: $errorMessage")
}

private fun shikimoriGraphQLPayload(query: String, searchOrId: String): String {
    return buildJsonObject {
        put("query", query)
        putJsonObject("variables") {
            put("query", searchOrId)
            put("id", searchOrId)
        }
    }.toString()
}

private fun shikimoriGraphQLMutationPayload(
    mutation: String,
    userId: Long,
    targetId: Long,
    targetType: String,
    chapters: Long? = null,
    episodes: Long? = null,
    score: Long = 0,
    status: String,
): String {
    return buildJsonObject {
        put("query", mutation)
        putJsonObject("variables") {
            put("userId", userId)
            put("targetId", targetId)
            put("targetType", targetType)
            if (chapters != null) put("chapters", chapters)
            if (episodes != null) put("episodes", episodes)
            put("score", score)
            put("status", status)
        }
    }.toString()
}

private fun SMGraphQLPoster?.toCoverUrl(): String {
    return this?.mainUrl?.ifBlank { null }
        ?: this?.originalUrl?.ifBlank { null }
        ?: ""
}

private fun String.toShikimoriUrl(): String {
    return if (startsWith("http://") || startsWith("https://")) {
        this
    } else {
        ShikimoriApi.BASE_URL + this
    }
}
