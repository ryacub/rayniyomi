package tachiyomi.domain.release.interactor

import tachiyomi.domain.release.model.ReleaseQuality

class ReleaseClassifier {

    fun classify(
        tagName: String,
        prerelease: Boolean,
    ): ReleaseQuality {
        if (prerelease) return ReleaseQuality.PRERELEASE
        if (hasPrereleaseLikePattern(tagName)) return ReleaseQuality.PRERELEASE
        return ReleaseQuality.STABLE
    }

    private fun hasPrereleaseLikePattern(tagName: String): Boolean {
        if (tagName.isEmpty()) return false
        val lowerTag = tagName.lowercase()
        val patterns = listOf("rc", "beta", "alpha", "pre", "dev", "release-candidate")
        return patterns.any { pattern -> lowerTag.contains(pattern) }
    }
}
