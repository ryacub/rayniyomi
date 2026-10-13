package xyz.rayniyomi.cloudstream.host

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.EpisodeResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode

fun LoadResponse.toSAnime(): SAnime = SAnime.create().also {
    it.url = url
    it.title = name
    it.description = plot
    it.genre = tags?.joinToString(", ")
    it.thumbnail_url = posterUrl
    it.status = when ((this as? EpisodeResponse)?.showStatus) {
        ShowStatus.Ongoing -> SAnime.ONGOING
        ShowStatus.Completed -> SAnime.COMPLETED
        null -> SAnime.UNKNOWN
    }
    it.initialized = true
}

fun LoadResponse.toEpisodes(): List<SEpisode> = when (this) {
    is AnimeLoadResponse ->
        DUB_ORDER
            .flatMap { status -> episodes[status].orEmpty().toSEpisodes(status.takeIf { it != DubStatus.None }?.name) }
            .distinctBy { it.url }
            .reversed()
    is TvSeriesLoadResponse -> episodes.toSEpisodes(scanlator = null).distinctBy { it.url }.reversed()
    is MovieLoadResponse -> listOf(
        SEpisode.create().also {
            it.url = dataUrl
            it.name = name
            it.episode_number = 1f
        },
    )
    else -> throw UnsupportedOperationException("CloudStream $type content is not supported")
}

private val DUB_ORDER = listOf(DubStatus.Subbed, DubStatus.Dubbed, DubStatus.None)

private fun List<Episode>.toSEpisodes(scanlator: String?): List<SEpisode> = mapIndexed { index, episode ->
    val number = episode.episode ?: (index + 1)
    SEpisode.create().also {
        it.url = episode.data
        it.name = episode.name ?: "Episode $number"
        it.episode_number = number.toFloat()
        it.date_upload = episode.date ?: 0L
        it.scanlator = scanlator
        it.summary = episode.description
        it.preview_url = episode.posterUrl
    }
}
