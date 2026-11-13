package eu.kanade.domain.items.episode.model

import eu.kanade.tachiyomi.animesource.model.SEpisode
import tachiyomi.domain.items.episode.model.Episode

fun Episode.toSEpisode(): SEpisode {
    return SEpisode.create().also {
        it.url = url
        it.name = name
        it.date_upload = dateUpload
        it.episode_number = episodeNumber.toFloat()
        it.fillermark = fillermark
        it.scanlator = scanlator
        it.summary = summary
        it.preview_url = previewUrl
        it.memo = memo
    }
}

fun List<Episode>.toSourceEpisodes(): List<SEpisode> = sortedBy(Episode::sourceOrder).map(Episode::toSEpisode)

fun Episode.copyFromSEpisode(sEpisode: SEpisode): Episode {
    return this.copy(
        name = sEpisode.name,
        url = sEpisode.url,
        dateUpload = sEpisode.date_upload,
        episodeNumber = sEpisode.episode_number.toDouble(),
        fillermark = sEpisode.fillermark,
        scanlator = sEpisode.scanlator?.ifBlank { null },
        summary = sEpisode.summary?.ifBlank { null },
        previewUrl = sEpisode.preview_url?.ifBlank { null },
        memo = sEpisode.memo,
    )
}
