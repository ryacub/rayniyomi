package eu.kanade.tachiyomi.ui.library.anime

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
import tachiyomi.source.local.entries.anime.LocalAnimeSource

fun QueryNode.matches(item: AnimeLibraryItem): Boolean {
    return when (this) {
        is AndNode -> children.all { it.matches(item) }
        is OrNode -> children.any { it.matches(item) }
        is NotNode -> !child.matches(item)
        is EmptyQueryNode -> true
        is GeneralQueryNode -> matches(item)
        is FieldQueryNode -> matches(item)
    }
}

fun AnimeLibraryItem.matchesQuery(query: String): Boolean {
    return if (isLegacySearchQuery(query)) {
        matches(query)
    } else {
        parseSearchQuery(query).matches(this)
    }
}

private fun GeneralQueryNode.matches(item: AnimeLibraryItem): Boolean {
    val anime = item.libraryAnime.anime

    val match = MangaField.entries.any { field ->
        when (field) {
            MangaField.TITLE -> anime.title.contains(value, ignoreCase = true)
            MangaField.AUTHOR -> anime.author?.contains(value, ignoreCase = true) ?: false
            MangaField.ARTIST -> anime.artist?.contains(value, ignoreCase = true) ?: false
            MangaField.DESCRIPTION -> anime.description?.contains(value, ignoreCase = true) ?: false
            MangaField.GENRE -> anime.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            MangaField.SOURCE -> {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && anime.source == LocalAnimeSource.ID)
            }
        }
    }
    return if (negated) !match else match
}

private fun FieldQueryNode.matches(item: AnimeLibraryItem): Boolean {
    val anime = item.libraryAnime.anime

    val match = when (field) {
        MangaField.GENRE -> {
            if (value.isEmpty()) {
                anime.genre.isNullOrEmpty()
            } else {
                anime.genre?.any { it.contains(value, ignoreCase = true) } ?: false
            }
        }

        MangaField.SOURCE -> {
            if (value.isEmpty()) {
                item.sourceName.isEmpty()
            } else {
                item.sourceName.contains(value, ignoreCase = true) ||
                    (value.equals("local", ignoreCase = true) && anime.source == LocalAnimeSource.ID)
            }
        }

        else -> {
            val text = when (field) {
                MangaField.TITLE -> anime.title
                MangaField.AUTHOR -> anime.author
                MangaField.ARTIST -> anime.artist
                MangaField.DESCRIPTION -> anime.description

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
