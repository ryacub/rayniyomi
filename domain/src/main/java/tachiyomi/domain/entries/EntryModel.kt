package tachiyomi.domain.entries

/**
 * Shared interface for fields common to both Anime and Manga domain models.
 */
interface EntryModel {
    val id: Long
    val source: Long
    val favorite: Boolean
    val lastUpdate: Long
    val nextUpdate: Long
    val fetchInterval: Int
    val dateAdded: Long

    /**
     * Bitmask flags with entity-specific semantics:
     * - Anime: encodes skip-intro length, airing episode info, and airing time
     * - Manga: encodes reading mode and reader orientation
     */
    val viewerFlags: Long
    val coverLastModified: Long
    val url: String
    val title: String
    val artist: String?
    val author: String?
    val description: String?
    val genre: List<String>?
    val status: Long
    val thumbnailUrl: String?
    val initialized: Boolean
    val lastModifiedAt: Long
    val favoriteModifiedAt: Long?
    val version: Long
}
