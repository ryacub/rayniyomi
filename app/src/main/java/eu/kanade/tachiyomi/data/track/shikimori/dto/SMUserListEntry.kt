package eu.kanade.tachiyomi.data.track.shikimori.dto

import eu.kanade.tachiyomi.data.database.models.anime.AnimeTrack
import eu.kanade.tachiyomi.data.database.models.manga.MangaTrack
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.data.track.shikimori.toTrackStatus
import kotlinx.serialization.Serializable

@Serializable
data class SMUserListEntry(
    val id: Long,
    val chapters: Double,
    val episodes: Double,
    val score: Int,
    val status: String,
) {
    internal fun toMangaTrack(trackId: Long, mediaId: Long, manga: SMGraphQLEntry?): MangaTrack {
        val searchTrack = manga?.toMangaTrack(trackId)
        return MangaTrack.create(trackId).apply {
            title = searchTrack?.title.orEmpty()
            remote_id = searchTrack?.remote_id ?: mediaId
            total_chapters = searchTrack?.total_chapters ?: 0L
            library_id = this@SMUserListEntry.id
            last_chapter_read = this@SMUserListEntry.chapters
            score = this@SMUserListEntry.score.toDouble()
            status = toTrackStatus(this@SMUserListEntry.status)
            tracking_url = searchTrack?.tracking_url ?: ShikimoriApi.BASE_URL
        }
    }

    internal fun toAnimeTrack(trackId: Long, mediaId: Long, anime: SMGraphQLEntry?): AnimeTrack {
        val searchTrack = anime?.toAnimeTrack(trackId)
        return AnimeTrack.create(trackId).apply {
            title = searchTrack?.title.orEmpty()
            remote_id = searchTrack?.remote_id ?: mediaId
            total_episodes = searchTrack?.total_episodes ?: 0L
            library_id = this@SMUserListEntry.id
            last_episode_seen = this@SMUserListEntry.episodes
            score = this@SMUserListEntry.score.toDouble()
            status = toTrackStatus(this@SMUserListEntry.status)
            tracking_url = searchTrack?.tracking_url ?: ShikimoriApi.BASE_URL
        }
    }
}
