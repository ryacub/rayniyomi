package tachiyomi.domain.entries

/**
 * Shared cover data needed by cover fetchers.
 * Implementing classes provide entry-specific aliases (e.g. animeId, mangaId).
 */
interface EntryCover {
    val entryId: Long
    val sourceId: Long
    val isFavorite: Boolean
    val url: String?
    val lastModified: Long
}
