package eu.kanade.tachiyomi.data.track.shikimori.dto

import eu.kanade.tachiyomi.data.track.model.AnimeTrackSearch
import eu.kanade.tachiyomi.data.track.model.MangaTrackSearch
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

@Serializable
internal data class SMGraphQLResponse(
    val data: SMGraphQLData? = null,
    val errors: List<SMGraphQLError> = emptyList(),
)

@Serializable
internal data class SMGraphQLData(
    val mangas: List<SMGraphQLEntry> = emptyList(),
    val animes: List<SMGraphQLEntry> = emptyList(),
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
