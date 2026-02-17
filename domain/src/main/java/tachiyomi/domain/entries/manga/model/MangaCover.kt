package tachiyomi.domain.entries.manga.model

import tachiyomi.domain.entries.EntryCover

/**
 * Contains the required data for MangaCoverFetcher
 */
data class MangaCover(
    val mangaId: Long,
    override val sourceId: Long,
    val isMangaFavorite: Boolean,
    override val url: String?,
    override val lastModified: Long,
) : EntryCover {
    override val entryId: Long get() = mangaId
    override val isFavorite: Boolean get() = isMangaFavorite
}

fun Manga.asMangaCover(): MangaCover {
    return MangaCover(
        mangaId = id,
        sourceId = source,
        isMangaFavorite = favorite,
        url = thumbnailUrl,
        lastModified = coverLastModified,
    )
}
