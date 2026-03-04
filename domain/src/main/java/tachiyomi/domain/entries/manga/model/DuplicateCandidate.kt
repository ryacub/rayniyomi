package tachiyomi.domain.entries.manga.model

data class DuplicateCandidate(
    val winner: Manga,
    val loser: Manga,
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
