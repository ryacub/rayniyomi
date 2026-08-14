package tachiyomi.domain.library.model.search

enum class MangaField(vararg val aliases: String) {
    TITLE("title"),
    AUTHOR("author"),
    ARTIST("artist"),
    DESCRIPTION("description", "desc"),
    GENRE("genre", "tag"),
    SOURCE("source", "src"),
    ;

    companion object {
        private val lookup = entries.flatMap { field ->
            field.aliases.map { it.lowercase() to field }
        }.toMap()

        fun fromString(value: String): MangaField? = lookup[value.lowercase()]
    }
}

sealed interface QueryNode {
    companion object {
        fun from(query: String): QueryNode {
            val tokens = LibrarySearchLexer.tokenize(query)
            return LibrarySearchParser(tokens).parse()
        }
    }
}

data class AndNode(val children: List<QueryNode>) : QueryNode

data class OrNode(val children: List<QueryNode>) : QueryNode

data class NotNode(val child: QueryNode) : QueryNode

/**
 * Matches every item. The parser returns this node for unresolvable input, and parseSearchQuery
 * uses it as its failure fallback. The evaluator treats it as a match, so a malformed search can
 * never empty the library.
 */
object EmptyQueryNode : QueryNode

data class GeneralQueryNode(val value: String, val negated: Boolean) : QueryNode

data class FieldQueryNode(val field: MangaField, val value: String, val negated: Boolean) : QueryNode
