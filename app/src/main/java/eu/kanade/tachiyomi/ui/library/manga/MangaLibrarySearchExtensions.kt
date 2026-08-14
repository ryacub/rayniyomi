package eu.kanade.tachiyomi.ui.library.manga

import tachiyomi.domain.library.model.search.AndNode
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

fun QueryNode.matches(item: MangaLibraryItem): Boolean {
    return when (this) {
        is AndNode -> children.all { it.matches(item) }
        is OrNode -> children.any { it.matches(item) }
        is NotNode -> !child.matches(item)
        is EmptyQueryNode -> true
        is GeneralQueryNode -> matches(item)
        is FieldQueryNode -> matches(item)
    }
}

fun MangaLibraryItem.matchesQuery(query: String): Boolean = librarySearchMatcher(query)(this)

/**
 * Builds a matcher for one query. It routes and parses the query once per library
 * emission instead of once per item.
 */
fun librarySearchMatcher(query: String?): (MangaLibraryItem) -> Boolean {
    if (query == null) return { true }
    if (isLegacySearchQuery(query)) return { item -> item.matches(query) }
    val node = parseSearchQuery(query)
    return { item -> node.matches(item) }
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

        else -> {
            val text = when (field) {
                LibrarySearchField.TITLE -> manga.title
                LibrarySearchField.AUTHOR -> manga.author
                LibrarySearchField.ARTIST -> manga.artist
                LibrarySearchField.DESCRIPTION -> manga.description

                // unreachable; added here to make the `when` exhaustive
                LibrarySearchField.GENRE, LibrarySearchField.SOURCE -> error("unreachable")
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
