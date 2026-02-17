package tachiyomi.domain.entries.anime.model

import tachiyomi.domain.entries.EntryCover

/**
 * Contains the required data for AnimeCoverFetcher
 */
data class AnimeCover(
    val animeId: Long,
    override val sourceId: Long,
    val isAnimeFavorite: Boolean,
    override val url: String?,
    override val lastModified: Long,
) : EntryCover {
    override val entryId: Long get() = animeId
    override val isFavorite: Boolean get() = isAnimeFavorite
}

fun Anime.asAnimeCover(): AnimeCover {
    return AnimeCover(
        animeId = id,
        sourceId = source,
        isAnimeFavorite = favorite,
        url = thumbnailUrl,
        lastModified = coverLastModified,
    )
}
