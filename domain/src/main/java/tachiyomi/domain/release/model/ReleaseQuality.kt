package tachiyomi.domain.release.model

/**
 * Classification for release quality and stability.
 */
enum class ReleaseQuality {
    /** Stable, production-ready release */
    STABLE,

    /** Pre-release version (alpha, beta, rc, etc.) */
    PRERELEASE,

    /** Draft release (not published) */
    DRAFT,

    /** Deprecated release (should not be used) */
    DEPRECATED,
}
