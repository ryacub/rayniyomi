package eu.kanade.tachiyomi.util
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.simkl.Simkl
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.jsonMime
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.anime.model.Anime
import java.io.IOException
import java.time.Instant
import java.time.OffsetDateTime

class AniChartApi(private val client: OkHttpClient = appGraph.networkHelper.client) {
    private val json = Json { ignoreUnknownKeys = true }

    internal suspend fun loadAiringTime(
        anime: Anime,
        trackItems: List<AnimeTrackItem>,
        manualFetch: Boolean,
    ): Pair<Int, Long> {
        var airingEpisodeData = Pair(anime.nextEpisodeToAir, anime.nextEpisodeAiringAt)
        if (anime.status == SAnime.COMPLETED.toLong() && !manualFetch) return airingEpisodeData

        return withIOContext {
            val matchingTrackItem = trackItems.firstOrNull {
                (it.tracker is Anilist && it.track != null) ||
                    (it.tracker is MyAnimeList && it.track != null) ||
                    (it.tracker is Simkl && it.track != null)
            } ?: return@withIOContext Pair(1, 0L)

            matchingTrackItem.let { item ->
                item.track!!.let {
                    airingEpisodeData = when (item.tracker) {
                        is Anilist -> getAnilistAiringEpisodeData(it.remoteId)
                        is MyAnimeList -> getAnilistAiringEpisodeData(getAlIdFromMal(it.remoteId))
                        is Simkl -> getSimklAiringEpisodeData(it.remoteId)
                        else -> Pair(1, 0L)
                    }
                }
            }
            return@withIOContext airingEpisodeData
        }
    }

    private suspend fun getAlIdFromMal(idMal: Long): Long {
        return withIOContext {
            val query = """
                query {
                    Media(idMal:$idMal,type: ANIME) {
                        id
                    }
                }
            """.trimMargin()

            val response = try {
                client.newCall(
                    POST(
                        "https://graphql.anilist.co",
                        body = buildJsonObject { put("query", query) }.toString()
                            .toRequestBody(jsonMime),
                    ),
                ).execute()
            } catch (e: Exception) {
                return@withIOContext 0L
            }
            return@withIOContext response.body.string().substringAfter("id\":")
                .substringBefore("}")
                .toLongOrNull() ?: 0L
        }
    }

    private suspend fun getAnilistAiringEpisodeData(id: Long): Pair<Int, Long> {
        return withIOContext {
            val query = """
                query {
                    Media(id:$id) {
                        nextAiringEpisode {
                            episode
                            airingAt
                        }
                    }
                }
            """.trimMargin()
            val response = try {
                client.newCall(
                    POST(
                        "https://graphql.anilist.co",
                        body = buildJsonObject { put("query", query) }.toString()
                            .toRequestBody(jsonMime),
                    ),
                ).execute()
            } catch (e: Exception) {
                return@withIOContext Pair(1, 0L)
            }
            val data = response.body.string()
            val episodeNumber = data.substringAfter("episode\":").substringBefore(",").toIntOrNull() ?: 1
            val airingAt = data.substringAfter("airingAt\":").substringBefore("}").toLongOrNull() ?: 0L

            return@withIOContext Pair(episodeNumber, airingAt)
        }
    }

    private suspend fun getSimklAiringEpisodeData(id: Long): Pair<Int, Long> = withIOContext {
        val entries = readSimklCalendar().filter { it.ids.simklId == id }
        val malId = entries.firstNotNullOfOrNull { it.ids.mal?.contentOrNull?.toLongOrNull()?.takeIf { it != 0L } }
        if (malId != null) return@withIOContext getAnilistAiringEpisodeData(getAlIdFromMal(malId))

        val now = Instant.now().epochSecond
        entries.mapNotNull { entry ->
            val airingAt = runCatching { toUnixTimestamp(entry.date) }.getOrNull() ?: return@mapNotNull null
            Pair(entry.episode?.episode ?: 1, airingAt)
        }
            .filter { (_, airingAt) -> airingAt > now }
            .minByOrNull { (_, airingAt) -> airingAt }
            ?: Pair(1, 0L)
    }

    private suspend fun readSimklCalendar(): List<SimklCalendarEntry> {
        return try {
            client.newCall(GET("https://data.simkl.in/calendar/anime.json")).await().use { response ->
                if (!response.isSuccessful) return emptyList()
                json.decodeFromString<List<SimklCalendarEntry>>(response.body.string())
            }
        } catch (e: IOException) {
            emptyList()
        } catch (e: SerializationException) {
            emptyList()
        }
    }

    private fun toUnixTimestamp(dateFormat: String): Long {
        val offsetDateTime = OffsetDateTime.parse(dateFormat)
        val instant = offsetDateTime.toInstant()
        return instant.epochSecond
    }
}

@Serializable
private data class SimklCalendarEntry(
    val date: String,
    val ids: SimklCalendarIds,
    val episode: SimklCalendarEpisode? = null,
)

@Serializable
private data class SimklCalendarIds(
    @SerialName("simkl_id") val simklId: Long,
    val mal: JsonPrimitive? = null,
)

@Serializable
private data class SimklCalendarEpisode(val episode: Int)
