package eu.kanade.tachiyomi.extension

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
