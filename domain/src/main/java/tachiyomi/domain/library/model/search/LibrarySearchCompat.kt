package tachiyomi.domain.library.model.search

/**
 * Compiled once: the routing check runs for every item on every library emission.
 * The alias set comes from [LibrarySearchField], which is the source of truth, so
 * a new alias routes without a matching edit here.
 */
private val FIELD_PREFIX_REGEX = Regex(
    "(?<![a-zA-Z0-9_])(" +
        LibrarySearchField.entries.flatMap { field -> field.aliases.toList() }
            .joinToString("|") +
        "):",
    RegexOption.IGNORE_CASE,
)

/**
 * Compiled once: the routing check runs for every item on every library emission.
 * The alias and symbol sets come from [ComparisonField] and [ComparisonOperator],
 * which are the sources of truth, so a new alias or symbol routes without a
 * matching edit here. Symbols are sorted longest-first so two-character operators
 * match before their one-character prefixes.
 */
private val COMPARISON_REGEX = Regex(
    "(?<![a-zA-Z0-9_])(" +
        ComparisonField.entries.flatMap { field -> field.aliases.toList() }
            .joinToString("|") +
        ")" +
        "(" +
        ComparisonOperator.entries
            .map { it.symbol }
            .sortedByDescending { it.length }
            .joinToString("|") +
        ")",
    RegexOption.IGNORE_CASE,
)

/**
 * Routes a library search query to the legacy matcher or the parsed grammar.
 *
 * A query stays legacy unless it starts with "id:" (case-insensitive) or uses an operator
 * (`&&`, `||`), a double quote, a known field prefix, or a known comparison field. Single
 * quotes and parentheses do not route to the new grammar, so queries such as "Gintama'" or
 * "(G)I-DLE" keep their legacy behavior.
 */
fun isLegacySearchQuery(query: String): Boolean {
    if (query.startsWith("id:", true)) return true
    val usesNewGrammar = query.contains("&&") ||
        query.contains("||") ||
        query.contains("\"") ||
        FIELD_PREFIX_REGEX.containsMatchIn(query) ||
        COMPARISON_REGEX.containsMatchIn(query)
    return !usesNewGrammar
}

/**
 * Parses a query into a node tree. Never throws: on any failure it returns [EmptyQueryNode],
 * which matches every item, so a malformed expression can never crash or empty the library.
 */
fun parseSearchQuery(query: String): QueryNode {
    return runCatching { QueryNode.from(query) }.getOrDefault(EmptyQueryNode)
}
