package eu.kanade.tachiyomi.ui.library.manga

import tachiyomi.domain.library.model.search.AndNode
import tachiyomi.domain.library.model.search.EmptyQueryNode
import tachiyomi.domain.library.model.search.FieldQueryNode
import tachiyomi.domain.library.model.search.GeneralQueryNode
import tachiyomi.domain.library.model.search.MangaField
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

fun MangaLibraryItem.matchesQuery(query: String): Boolean {
    return if (isLegacySearchQuery(query)) {
        matches(query)
    } else {
        parseSearchQuery(query).matches(this)
    }
}

private fun GeneralQueryNode.matches(item: MangaLibraryItem): Boolean {
    val manga = item.libraryManga.manga

    val match = MangaField.entries.any { field ->
        when (field) {
            MangaField.TITLE -> manga.title.contains(value, ignoreCase = true)
            MangaField.AUTHOR -> manga.author?.contains(value, ignoreCase = true) ?: false
            MangaField.ARTIST -> manga.artist?.contains(value, ignoreCase = true) ?: false
            MangaField.DESCRIPTION -> manga.description?.contains(value, ignoreCase = true) ?: false
            MangaField.GENRE -> manga.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            MangaField.SOURCE -> {
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
        MangaField.GENRE -> {
            if (value.isEmpty()) {
                manga.genre.isNullOrEmpty()
            } else {
                manga.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            }
        }

        MangaField.SOURCE -> {
            if (value.isEmpty()) {
                item.sourceName.isEmpty()
            } else {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && manga.source == LocalMangaSource.ID)
            }
        }

        else -> {
            val text = when (field) {
                MangaField.TITLE -> manga.title
                MangaField.AUTHOR -> manga.author
                MangaField.ARTIST -> manga.artist
                MangaField.DESCRIPTION -> manga.description

                // unreachable; added here to make the `when` exhaustive
                MangaField.GENRE, MangaField.SOURCE -> error("unreachable")
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