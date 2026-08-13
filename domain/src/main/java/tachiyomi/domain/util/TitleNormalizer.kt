package tachiyomi.domain.util

object TitleNormalizer {

    private val leadingArticles = Regex("^(the|a|an)\\s+", RegexOption.IGNORE_CASE)

    // Android rejects the inline (?U) flag on some runtime paths.
    private val punctuation = Regex("[^\\p{L}\\p{M}\\p{N}_\\s]")
    private val extraWhitespace = Regex("\\s+")

    /**
     * Normalizes a title for duplicate detection:
     * - Lowercases
     * - Strips leading articles ("The", "A", "An")
     * - Removes punctuation
     * - Collapses whitespace
     */
    fun normalize(title: String): String {
        return title
            .trim()
            .lowercase()
            .replace(leadingArticles, "")
            .replace(punctuation, " ")
            .replace(extraWhitespace, " ")
            .trim()
    }
}
