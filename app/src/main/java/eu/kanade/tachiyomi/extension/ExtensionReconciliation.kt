package eu.kanade.tachiyomi.extension

/**
 * Selects the preferred extension candidate when multiple candidates exist for the same extension
 * (e.g., same extension available from different repositories with different signatures).
 *
 * **Selection Priority:**
 * 1. **Signature match (highest)** - Candidate whose signing key fingerprint matches the installed extension's signature hash
 * 2. **Repository URL match (fallback)** - Candidate whose repo URL matches where the installed extension came from
 * 3. **First candidate (default)** - If no signature or repo match, returns the first available candidate
 *
 * This prioritization ensures:
 * - Updates from the same signing authority are preferred (most secure)
 * - Extensions stay with their original repository when possible (stability)
 * - Extensions can still update even when switching repos (flexibility)
 *
 * **Security Note:** Signature matching uses the FIRST signature from multi-signature APKs for deterministic
 * comparison across Android API levels (v1/v2/v3/v4 signing schemes).
 *
 * @param T Extension candidate type (e.g., AnimeExtension.Available, MangaExtension.Available)
 * @param candidates List of extension candidates from different repos (must not be empty)
 * @param installedSignatureHash SHA-256 hash of installed extension's signing certificate (nullable if not installed)
 * @param installedRepoUrl Repository URL where installed extension came from (nullable if unknown)
 * @param candidateSignatureHash Extractor function to get signing key fingerprint from candidate
 * @param candidateRepoUrl Extractor function to get repository URL from candidate
 * @return The preferred candidate based on priority rules
 * @throws IllegalArgumentException if candidates list is empty
 */
internal fun <T> selectPreferredExtensionCandidate(
    candidates: List<T>,
    installedSignatureHash: String?,
    installedRepoUrl: String?,
    candidateSignatureHash: (T) -> String,
    candidateRepoUrl: (T) -> String,
): T {
    require(candidates.isNotEmpty()) { "candidates must not be empty" }

    installedSignatureHash
        ?.let { signature -> candidates.firstOrNull { candidateSignatureHash(it) == signature } }
        ?.let { return it }

    installedRepoUrl
        ?.let { repoUrl -> candidates.firstOrNull { candidateRepoUrl(it) == repoUrl } }
        ?.let { return it }

    return candidates.first()
}
