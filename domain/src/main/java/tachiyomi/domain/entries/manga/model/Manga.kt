package tachiyomi.domain.entries.manga.model

import androidx.compose.runtime.Immutable
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.EntryModel
import java.io.Serializable
import java.time.Instant

@Immutable
data class Manga(
    override val id: Long,
    override val source: Long,
    override val favorite: Boolean,
    override val lastUpdate: Long,
    override val nextUpdate: Long,
    override val fetchInterval: Int,
    override val dateAdded: Long,
    override val viewerFlags: Long,
    val chapterFlags: Long,
    override val coverLastModified: Long,
    override val url: String,
    override val title: String,
    override val artist: String?,
    override val author: String?,
    override val description: String?,
    override val genre: List<String>?,
    override val status: Long,
    override val thumbnailUrl: String?,
    val updateStrategy: UpdateStrategy,
    override val initialized: Boolean,
    override val lastModifiedAt: Long,
    override val favoriteModifiedAt: Long?,
    override val version: Long,
) : EntryModel, Serializable {

    val expectedNextUpdate: Instant?
        get() = nextUpdate
            .takeIf { status != SManga.COMPLETED.toLong() }
            ?.let { Instant.ofEpochMilli(it) }

    val sorting: Long
        get() = chapterFlags and CHAPTER_SORTING_MASK

    val displayMode: Long
        get() = chapterFlags and CHAPTER_DISPLAY_MASK

    val unreadFilterRaw: Long
        get() = chapterFlags and CHAPTER_UNREAD_MASK

    val downloadedFilterRaw: Long
        get() = chapterFlags and CHAPTER_DOWNLOADED_MASK

    val bookmarkedFilterRaw: Long
        get() = chapterFlags and CHAPTER_BOOKMARKED_MASK

    val unreadFilter: TriState
        get() = when (unreadFilterRaw) {
            CHAPTER_SHOW_UNREAD -> TriState.ENABLED_IS
            CHAPTER_SHOW_READ -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    val bookmarkedFilter: TriState
        get() = when (bookmarkedFilterRaw) {
            CHAPTER_SHOW_BOOKMARKED -> TriState.ENABLED_IS
            CHAPTER_SHOW_NOT_BOOKMARKED -> TriState.ENABLED_NOT
            else -> TriState.DISABLED
        }

    fun sortDescending(): Boolean {
        return chapterFlags and CHAPTER_SORT_DIR_MASK == CHAPTER_SORT_DESC
    }

    companion object {
        // Generic filter that does not filter anything
        const val SHOW_ALL = 0x00000000L

        const val CHAPTER_SORT_DESC = 0x00000000L
        const val CHAPTER_SORT_ASC = 0x00000001L
        const val CHAPTER_SORT_DIR_MASK = 0x00000001L

        const val CHAPTER_SHOW_UNREAD = 0x00000002L
        const val CHAPTER_SHOW_READ = 0x00000004L
        const val CHAPTER_UNREAD_MASK = 0x00000006L

        const val CHAPTER_SHOW_DOWNLOADED = 0x00000008L
        const val CHAPTER_SHOW_NOT_DOWNLOADED = 0x00000010L
        const val CHAPTER_DOWNLOADED_MASK = 0x00000018L

        const val CHAPTER_SHOW_BOOKMARKED = 0x00000020L
        const val CHAPTER_SHOW_NOT_BOOKMARKED = 0x00000040L
        const val CHAPTER_BOOKMARKED_MASK = 0x00000060L

        const val CHAPTER_SORTING_SOURCE = 0x00000000L
        const val CHAPTER_SORTING_NUMBER = 0x00000100L
        const val CHAPTER_SORTING_UPLOAD_DATE = 0x00000200L
        const val CHAPTER_SORTING_ALPHABET = 0x00000300L
        const val CHAPTER_SORTING_MASK = 0x00000300L

        const val CHAPTER_DISPLAY_NAME = 0x00000000L
        const val CHAPTER_DISPLAY_NUMBER = 0x00100000L
        const val CHAPTER_DISPLAY_MASK = 0x00100000L

        fun create() = Manga(
            id = -1L,
            url = "",
            title = "",
            source = -1L,
            favorite = false,
            lastUpdate = 0L,
            nextUpdate = 0L,
            fetchInterval = 0,
            dateAdded = 0L,
            viewerFlags = 0L,
            chapterFlags = 0L,
            coverLastModified = 0L,
            artist = null,
            author = null,
            description = null,
            genre = null,
            status = 0L,
            thumbnailUrl = null,
            updateStrategy = UpdateStrategy.ALWAYS_UPDATE,
            initialized = false,
            lastModifiedAt = 0L,
            favoriteModifiedAt = null,
            version = 0L,
        )
    }
}
