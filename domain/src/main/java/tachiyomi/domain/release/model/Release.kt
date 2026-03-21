package tachiyomi.domain.release.model

/**
 * Contains information about the latest release.
 */
data class Release(
    val version: String,
    val info: String,
    val releaseLink: String,
    val downloadLink: String,
    val quality: ReleaseQuality = ReleaseQuality.STABLE,
) {
    /**
     * Determines if this release is usable based on its quality and the includePrerelease setting.
     *
     * @param includePrerelease If true, prerelease versions are considered usable.
     *                          If false, only stable releases are usable.
     * @return true if the release should be offered to the user, false otherwise.
     */
    fun isUsable(includePrerelease: Boolean): Boolean {
        return when (quality) {
            ReleaseQuality.STABLE -> true
            ReleaseQuality.PRERELEASE -> includePrerelease
            ReleaseQuality.DRAFT -> false
            ReleaseQuality.DEPRECATED -> false
        }
    }
}
