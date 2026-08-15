package tachiyomi.domain.library.model.search

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

enum class LibrarySearchField(vararg val aliases: String, val fieldOnly: Boolean = false) {
    TITLE("title"),
    AUTHOR("author"),
    ARTIST("artist"),
    DESCRIPTION("description", "desc"),
    GENRE("genre", "tag"),
    SOURCE("source", "src"),
    LANGUAGE("language", "lang", fieldOnly = true),
    NOTES("notes", "note", fieldOnly = true),
    ;

    companion object {
        private val lookup = entries.flatMap { field ->
            field.aliases.map { it.lowercase() to field }
        }.toMap()

        fun fromString(value: String): LibrarySearchField? = lookup[value.lowercase()]
    }
}

enum class ComparisonField(vararg val aliases: String) {
    ID(),
    DATE_ADDED("added"),
    FETCH_INTERVAL("fetchinterval", "fi"),
    NEXT_UPDATE("nextupdate", "nu"),
    UNREAD(),
    READ(),
    TOTAL(),
    ;

    companion object {
        private val lookup = entries.flatMap { field ->
            (listOf(field.name) + field.aliases).map { it.lowercase() to field }
        }.toMap()

        fun fromString(value: String): ComparisonField? = lookup[value.lowercase()]
    }
}

enum class ComparisonOperator(val symbol: String) {
    GTE(">="),
    LTE("<="),
    GT(">"),
    LT("<"),
    EQ("="),
    ;

    companion object {
        fun fromString(value: String): ComparisonOperator? = entries.firstOrNull { it.symbol == value }
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

data class FieldQueryNode(val field: LibrarySearchField, val value: String, val negated: Boolean) : QueryNode

/**
 * Compares a typed field against a parsed value. The parsed value is cached once per node so a
 * single evaluation parses each value once, not once per item. The lazy properties are body
 * members, so they do not participate in data-class equality. A value that cannot be parsed
 * yields null, which the evaluator must turn into a non-match.
 */
data class ComparisonQueryNode(
    val field: ComparisonField,
    val value: String,
    val comparator: ComparisonOperator,
    val negated: Boolean,
) : QueryNode {
    private val parsedLong: Long? by lazy { value.toLongOrNull() }
    private val parsedDate: LocalDate? by lazy {
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * Applies the comparator to an actual numeric value. Returns null when the query value
     * cannot be parsed as a number.
     */
    fun compareLong(actual: Long): Boolean? = parsedLong?.let { expected ->
        when (comparator) {
            ComparisonOperator.GTE -> actual >= expected
            ComparisonOperator.LTE -> actual <= expected
            ComparisonOperator.GT -> actual > expected
            ComparisonOperator.LT -> actual < expected
            ComparisonOperator.EQ -> actual == expected
        }
    }

    /**
     * Applies the comparator to an actual date. Returns null when the query value cannot be
     * parsed as a date.
     */
    fun compareDate(actual: LocalDate): Boolean? = parsedDate?.let { expected ->
        when (comparator) {
            ComparisonOperator.GTE -> !actual.isBefore(expected)
            ComparisonOperator.LTE -> !actual.isAfter(expected)
            ComparisonOperator.GT -> actual.isAfter(expected)
            ComparisonOperator.LT -> actual.isBefore(expected)
            ComparisonOperator.EQ -> actual == expected
        }
    }
}

/**
 * Converts epoch milliseconds to a local date in the given zone. The zone is captured once
 * per evaluation and shared by every item so the conversion never re-resolves the system zone.
 */
fun epochMillisToLocalDate(epochMillis: Long, zone: ZoneId): LocalDate {
    return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
