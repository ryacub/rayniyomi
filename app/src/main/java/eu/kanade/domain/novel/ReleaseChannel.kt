package eu.kanade.domain.novel

/**
 * Represents the release maturity channel for a plugin build.
 *
 * The host uses the user's channel preference to decide which plugin releases are
 * eligible for installation. A [STABLE] host only accepts [STABLE] plugins; a
 * [BETA] host accepts both [STABLE] and [BETA] plugins.
 */
public enum class ReleaseChannel {
    /** Production-ready, fully validated releases. */
    STABLE,

    /** Pre-release builds for early adopters; may contain known issues. */
    BETA,
    ;

    companion object {
        /**
         * Deserialise a channel from its string representation as stored in
         * [NovelFeaturePreferences]. Unknown values fall back to [STABLE] so
         * the host stays in the safe lane after a downgrade.
         */
        public fun fromString(value: String): ReleaseChannel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STABLE
    }
}
