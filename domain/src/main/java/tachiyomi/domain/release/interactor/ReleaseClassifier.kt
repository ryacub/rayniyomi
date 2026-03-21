package tachiyomi.domain.release.interactor

import tachiyomi.domain.release.model.ReleaseQuality

/**
 * Classifies releases based on GitHub metadata and naming heuristics.
 *
 * Priority order (highest to lowest):
 * 1. draft=true → DRAFT
 * 2. prerelease=true → PRERELEASE
 * 3. Version pattern matching (rc, beta, alpha, pre, dev, release-candidate) → PRERELEASE
 * 4. Body contains "deprecated" → DEPRECATED
 * 5. Default → STABLE
 */
class ReleaseClassifier {

    fun classify(
        tagName: String,
        prerelease: Boolean,
        draft: Boolean,
        body: String?,
    ): ReleaseQuality {
        // Priority 1: Draft flag takes highest priority
        if (draft) {
            return ReleaseQuality.DRAFT
        }

        // Priority 2: Prerelease flag
        if (prerelease) {
            return ReleaseQuality.PRERELEASE
        }

        // Priority 3: Version string pattern matching
        if (hasPrereleaseLikePattern(tagName)) {
            return ReleaseQuality.PRERELEASE
        }

        // Priority 4: Check for deprecated marker in body
        if (hasDeprecatedMarker(body)) {
            return ReleaseQuality.DEPRECATED
        }

        // Priority 5: Default to stable
        return ReleaseQuality.STABLE
    }

    /**
     * Check if the tag name contains prerelease-like patterns.
     * Patterns: rc, beta, alpha, pre, dev, release-candidate (case-insensitive)
     */
    private fun hasPrereleaseLikePattern(tagName: String): Boolean {
        if (tagName.isEmpty()) {
            return false
        }

        val lowerTag = tagName.lowercase()
        val patterns = listOf("rc", "beta", "alpha", "pre", "dev", "release-candidate")

        return patterns.any { pattern ->
            // Match pattern with optional separators (-, ., or directly adjacent to numbers)
            // This handles: v1.0.0-rc1, v1.0.0rc1, v1.0.0-RC1, etc.
            lowerTag.contains(pattern)
        }
    }

    /**
     * Check if the release body contains the "deprecated" keyword (case-insensitive).
     */
    private fun hasDeprecatedMarker(body: String?): Boolean {
        if (body.isNullOrEmpty()) {
            return false
        }

        return body.lowercase().contains("deprecated")
    }
}
