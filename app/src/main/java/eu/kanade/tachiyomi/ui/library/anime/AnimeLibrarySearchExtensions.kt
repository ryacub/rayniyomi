package eu.kanade.tachiyomi.ui.library.anime

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
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import java.time.ZoneId
import kotlin.math.abs

fun QueryNode.matches(item: AnimeLibraryItem, zone: ZoneId): Boolean {
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

fun AnimeLibraryItem.matchesQuery(query: String): Boolean = librarySearchMatcher(query)(this)

/**
 * Builds a matcher for one query. It routes and parses the query once per library
 * emission instead of once per item. The time zone is captured once per emission too.
 */
fun librarySearchMatcher(query: String?): (AnimeLibraryItem) -> Boolean {
    if (query == null) return { true }
    if (isLegacySearchQuery(query)) return { item -> item.matches(query) }
    val node = parseSearchQuery(query)
    val zone = ZoneId.systemDefault()
    return { item -> node.matches(item, zone) }
}

private fun GeneralQueryNode.matches(item: AnimeLibraryItem): Boolean {
    val anime = item.libraryAnime.anime

    val match = LibrarySearchField.entries.any { field ->
        when (field) {
            LibrarySearchField.TITLE -> anime.title.contains(value, ignoreCase = true)
            LibrarySearchField.AUTHOR -> anime.author?.contains(value, ignoreCase = true) ?: false
            LibrarySearchField.ARTIST -> anime.artist?.contains(value, ignoreCase = true) ?: false
            LibrarySearchField.DESCRIPTION -> anime.description?.contains(value, ignoreCase = true) ?: false
            LibrarySearchField.GENRE -> anime.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            LibrarySearchField.SOURCE -> {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && anime.source == LocalAnimeSource.ID)
            }

            // Language and notes match only through their explicit field prefix,
            // never through general text search. This exhaustive `when` is the
            // single source of truth for which fields general search covers.
            LibrarySearchField.LANGUAGE, LibrarySearchField.NOTES -> false
        }
    }
    return if (negated) !match else match
}

private fun FieldQueryNode.matches(item: AnimeLibraryItem): Boolean {
    val anime = item.libraryAnime.anime

    val match = when (field) {
        LibrarySearchField.GENRE -> {
            if (value.isEmpty()) {
                anime.genre.isNullOrEmpty()
            } else {
                anime.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            }
        }

        LibrarySearchField.SOURCE -> {
            if (value.isEmpty()) {
                item.sourceName.isEmpty()
            } else {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && anime.source == LocalAnimeSource.ID)
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
                LibrarySearchField.TITLE -> anime.title
                LibrarySearchField.AUTHOR -> anime.author
                LibrarySearchField.ARTIST -> anime.artist
                LibrarySearchField.DESCRIPTION -> anime.description
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

private fun ComparisonQueryNode.matches(item: AnimeLibraryItem, zone: ZoneId): Boolean {
    val anime = item.libraryAnime.anime
    val libraryAnime = item.libraryAnime

    val match = when (field) {
        ComparisonField.ID -> compareLong(anime.id)
        ComparisonField.DATE_ADDED -> compareDate(epochMillisToLocalDate(anime.dateAdded, zone))
        ComparisonField.FETCH_INTERVAL -> compareLong(abs(anime.fetchInterval.toLong()))
        // Completed and never-scheduled entries have no next update. The accessor returns null
        // for them, so they match neither direction of the comparison.
        ComparisonField.NEXT_UPDATE ->
            anime.expectedNextUpdate
                ?.let { compareDate(it.atZone(zone).toLocalDate()) }
        ComparisonField.UNREAD -> compareLong(libraryAnime.unseenCount)
        ComparisonField.READ -> compareLong(libraryAnime.seenCount)
        ComparisonField.TOTAL -> compareLong(libraryAnime.totalCount)
    }

    return if (negated) match != true else match == true
}
