package tachiyomi.domain.library.model.search

/**
 * Compiled once: the routing check runs for every item on every library emission.
 */
private val FIELD_PREFIX_REGEX = Regex(
    "(?<![a-zA-Z0-9_])(title|author|artist|description|desc|genre|tag|source|src):",
    RegexOption.IGNORE_CASE,
)

/**
 * Routes a library search query to the legacy matcher or the parsed grammar.
 *
 * A query stays legacy unless it starts with "id:" (case-insensitive) or uses an operator
 * (`&&`, `||`), a double quote, or a known field prefix. Single quotes and parentheses do not
 * route to the new grammar, so queries such as "Gintama'" or "(G)I-DLE" keep their legacy
 * behavior.
 */
fun isLegacySearchQuery(query: String): Boolean {
    if (query.startsWith("id:", true)) return true
    val usesNewGrammar = query.contains("&&") ||
        query.contains("||") ||
        query.contains("\"") ||
        FIELD_PREFIX_REGEX.containsMatchIn(query)
    return !usesNewGrammar
}

/**
 * Parses a query into a node tree. Never throws: on any failure it returns [EmptyQueryNode],
 * which matches every item, so a malformed expression can never crash or empty the library.
 */
fun parseSearchQuery(query: String): QueryNode {
    return runCatching { QueryNode.from(query) }.getOrDefault(EmptyQueryNode)
}
