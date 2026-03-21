package tachiyomi.domain.release.model

enum class ReleaseQuality {
    /** Stable, production-ready release */
    STABLE,

    /** Pre-release version (alpha, beta, rc, etc.) */
    PRERELEASE,
}
