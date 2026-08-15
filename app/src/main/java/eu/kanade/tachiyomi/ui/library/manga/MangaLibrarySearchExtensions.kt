package eu.kanade.tachiyomi.ui.library.manga

import tachiyomi.domain.library.model.search.AndNode
import tachiyomi.domain.library.model.search.ComparisonField
import tachiyomi.domain.library.model.search.ComparisonQueryNode
import tachiyomi.domain.library.model.search.EmptyQueryNode
import tachiyomi.domain.library.model.search.FieldQueryNode
import tachiyomi.domain.library.model.search.GeneralQueryNode
import tachiyomi.domain.library.model.search.LibrarySearchField
import tachiyomi.domain.library.model.search.NotNode
import tachiyomi.domain.library.model.search.OrNode
import tachiyomi.domain.library.model.search.QueryNode
import tachiyomi.domain.library.model.search.isLegacySearchQuery
import tachiyomi.domain.library.model.search.parseSearchQuery
import tachiyomi.source.local.entries.manga.LocalMangaSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

fun QueryNode.matches(item: MangaLibraryItem, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    return when (this) {
        is AndNode -> children.all { it.matches(item, zone) }
        is OrNode -> children.any { it.matches(item, zone) }
        is NotNode -> !child.matches(item, zone)
        is EmptyQueryNode -> true
        is GeneralQueryNode -> matches(item)
        is FieldQueryNode -> matches(item)
        is ComparisonQueryNode -> matches(item, zone)
    }
}

fun MangaLibraryItem.matchesQuery(query: String): Boolean = librarySearchMatcher(query)(this)

/**
 * Builds a matcher for one query. It routes and parses the query once per library
 * emission instead of once per item. The time zone is captured once per emission too.
 */
fun librarySearchMatcher(query: String?): (MangaLibraryItem) -> Boolean {
    if (query == null) return { true }
    if (isLegacySearchQuery(query)) return { item -> item.matches(query) }
    val node = parseSearchQuery(query)
    val zone = ZoneId.systemDefault()
    return { item -> node.matches(item, zone) }
}

private fun GeneralQueryNode.matches(item: MangaLibraryItem): Boolean {
    val manga = item.libraryManga.manga

    val match = LibrarySearchField.entries.any { field ->
        when (field) {
            LibrarySearchField.TITLE -> manga.title.contains(value, ignoreCase = true)
            LibrarySearchField.AUTHOR -> manga.author?.contains(value, ignoreCase = true) ?: false
            LibrarySearchField.ARTIST -> manga.artist?.contains(value, ignoreCase = true) ?: false
            LibrarySearchField.DESCRIPTION -> manga.description?.contains(value, ignoreCase = true) ?: false
            LibrarySearchField.GENRE -> manga.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            LibrarySearchField.SOURCE -> {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && manga.source == LocalMangaSource.ID)
            }

            // fieldOnly fields are unavailable in general text search
            LibrarySearchField.LANGUAGE, LibrarySearchField.NOTES -> false
        }
    }
    return if (negated) !match else match
}

private fun FieldQueryNode.matches(item: MangaLibraryItem): Boolean {
    val manga = item.libraryManga.manga

    val match = when (field) {
        LibrarySearchField.GENRE -> {
            if (value.isEmpty()) {
                manga.genre.isNullOrEmpty()
            } else {
                manga.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            }
        }

        LibrarySearchField.SOURCE -> {
            if (value.isEmpty()) {
                item.sourceName.isEmpty()
            } else {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && manga.source == LocalMangaSource.ID)
            }
        }

        LibrarySearchField.LANGUAGE -> {
            if (value.isEmpty()) {
                item.sourceLang.isEmpty()
            } else {
                item.sourceLang.contains(value, ignoreCase = true)
            }
        }

        LibrarySearchField.NOTES -> value.isEmpty()

        else -> {
            val text = when (field) {
                LibrarySearchField.TITLE -> manga.title
                LibrarySearchField.AUTHOR -> manga.author
                LibrarySearchField.ARTIST -> manga.artist
                LibrarySearchField.DESCRIPTION -> manga.description

                // unreachable; added here to make the `when` exhaustive
                LibrarySearchField.GENRE, LibrarySearchField.SOURCE,
                LibrarySearchField.LANGUAGE, LibrarySearchField.NOTES,
                -> error("unreachable")
            }

            if (value.isEmpty()) {
                text.isNullOrEmpty()
            } else {
                text?.contains(value, ignoreCase = true) ?: false
            }
        }
    }

    return if (negated) !match else match
}

private fun ComparisonQueryNode.matches(item: MangaLibraryItem, zone: ZoneId): Boolean {
    val manga = item.libraryManga.manga
    val libraryManga = item.libraryManga

    val match = when (field) {
        ComparisonField.ID -> compareLong(manga.id)
        ComparisonField.DATE_ADDED -> compareDate(toLocalDate(manga.dateAdded, zone))
        ComparisonField.FETCH_INTERVAL -> compareLong(abs(manga.fetchInterval).toLong())
        ComparisonField.NEXT_UPDATE -> compareDate(toLocalDate(manga.nextUpdate, zone))
        ComparisonField.UNREAD -> compareLong(libraryManga.unreadCount)
        ComparisonField.READ -> compareLong(libraryManga.readCount)
        ComparisonField.TOTAL -> compareLong(libraryManga.totalChapters)
    }

    return if (negated) match != true else match == true
}

private fun toLocalDate(epochMillis: Long, zone: ZoneId): LocalDate {
    return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
