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
import tachiyomi.domain.library.model.search.epochMillisToLocalDate
import tachiyomi.domain.library.model.search.isLegacySearchQuery
import tachiyomi.domain.library.model.search.parseSearchQuery
import tachiyomi.source.local.entries.manga.LocalMangaSource
import java.time.ZoneId
import kotlin.math.abs

fun QueryNode.matches(item: MangaLibraryItem, zone: ZoneId): Boolean {
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

            // Language and notes match only through their explicit field prefix,
            // never through general text search. This exhaustive `when` is the
            // single source of truth for which fields general search covers.
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
                item.resolvedSourceLang.isEmpty()
            } else {
                item.resolvedSourceLang.contains(value, ignoreCase = true)
            }
        }

        LibrarySearchField.NOTES -> value.isEmpty()

        LibrarySearchField.TITLE,
        LibrarySearchField.AUTHOR,
        LibrarySearchField.ARTIST,
        LibrarySearchField.DESCRIPTION,
        -> {
            val text = when (field) {
                LibrarySearchField.TITLE -> manga.title
                LibrarySearchField.AUTHOR -> manga.author
                LibrarySearchField.ARTIST -> manga.artist
                LibrarySearchField.DESCRIPTION -> manga.description
                else -> error("unreachable")
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
        ComparisonField.DATE_ADDED -> compareDate(epochMillisToLocalDate(manga.dateAdded, zone))
        ComparisonField.FETCH_INTERVAL -> compareLong(abs(manga.fetchInterval.toLong()))
        // Completed and never-scheduled entries have no next update. The accessor returns null
        // for them, so they match neither direction of the comparison.
        ComparisonField.NEXT_UPDATE ->
            manga.expectedNextUpdate
                ?.let { compareDate(it.atZone(zone).toLocalDate()) }
        ComparisonField.UNREAD -> compareLong(libraryManga.unreadCount)
        ComparisonField.READ -> compareLong(libraryManga.readCount)
        ComparisonField.TOTAL -> compareLong(libraryManga.totalChapters)
    }

    return if (negated) match != true else match == true
}
