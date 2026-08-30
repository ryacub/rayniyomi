package tachiyomi.domain.library.model.search

import kotlin.text.get

object LibrarySearchLexer {

    private val regex = Regex(
        """
            # Operators
            (?<LParen> \( )|
            (?<RParen> \) )|
            (?<NOT> -(?![\s,]) )|
            (?<OR> \|\| )|
            (?<AND> && )|

            # Comparison fields: name operator value
            (?<CompField> [a-zA-Z_][a-zA-Z0-9_]* ) (?<Comparator> >=|<=|>|<|= )
            (?: \s* " (?<CompValQuoted> [^"]* ) " \s* | (?<CompVal> [^\s,()]+ ))|

            # Key-Value fields
            (?<Field> [a-zA-Z_][a-zA-Z0-9_]* ) :
            (?: " (?<FieldValQuoted> [^"]* ) " | (?<FieldVal> [^\s,()]+ ))|

            # General catch-all
            (?: " (?<GeneralQuoted> [^"]* ) " | (?<General> [^\s,()]+ ))|

            # Seperator
            (?<Separator> [\s,]+ )
        """.trimIndent(),
        RegexOption.COMMENTS,
    )

    sealed interface Token {
        data object LParen : Token
        data object RParen : Token
        data object And : Token
        data object Or : Token
        data object Not : Token
        data class CompField(val field: String, val comparator: ComparisonOperator, val value: String) : Token
        data class Field(val field: String, val value: String) : Token
        data class General(val value: String) : Token
    }

    fun tokenize(input: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val matches = regex.findAll(input)

        for (match in matches) {
            val groups = match.groups
            when {
                groups["Separator"] != null -> continue
                groups["AND"] != null -> tokens.add(Token.And)
                groups["OR"] != null -> tokens.add(Token.Or)
                groups["NOT"] != null -> tokens.add(Token.Not)
                groups["LParen"] != null -> tokens.add(Token.LParen)
                groups["RParen"] != null -> tokens.add(Token.RParen)
                groups["CompField"] != null -> {
                    val comparator = ComparisonOperator.fromString(
                        checkNotNull(groups["Comparator"]) { "Missing regex group: Comparator" }.value,
                    )
                    val value = groups["CompValQuoted"]?.value
                        ?: checkNotNull(groups["CompVal"]) { "Missing regex group: CompVal" }.value
                    if (comparator != null) {
                        tokens.add(
                            Token.CompField(
                                field = checkNotNull(groups["CompField"]) { "Missing regex group: CompField" }.value,
                                comparator = comparator,
                                value = value,
                            ),
                        )
                    } else {
                        tokens.add(
                            Token.General(
                                "${checkNotNull(groups["CompField"]) { "Missing regex group: CompField" }.value}" +
                                    "${checkNotNull(groups["Comparator"]) {
                                        "Missing regex group: Comparator"
                                    }.value}$value",
                            ),
                        )
                    }
                }
                groups["Field"] != null -> {
                    tokens.add(
                        Token.Field(
                            field = checkNotNull(groups["Field"]) { "Missing regex group: Field" }.value,
                            value = groups["FieldValQuoted"]?.value
                                ?: checkNotNull(groups["FieldVal"]) { "Missing regex group: FieldVal" }.value,
                        ),
                    )
                }
                else -> {
                    val value = groups["GeneralQuoted"]?.value
                        ?: checkNotNull(groups["General"]) { "Missing regex group: General" }.value
                    tokens.add(Token.General(value))
                }
            }
        }

        return tokens
    }
}
