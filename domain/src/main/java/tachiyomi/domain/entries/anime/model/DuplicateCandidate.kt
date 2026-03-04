package tachiyomi.domain.entries.anime.model

data class DuplicateCandidate(
    val winner: Anime,
    val loser: Anime,
    val confidence: DuplicateConfidence,
)

enum class DuplicateConfidence {
    /** Exact normalized title match */
    HIGH,

    /** Same tracker ID found on both entries */
    TRACKER,

    /** Normalized title similarity above threshold but not exact */
    MEDIUM,
}
