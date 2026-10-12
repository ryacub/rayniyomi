package eu.kanade.tachiyomi.extension

internal data class ExtensionCatalog<T>(
    val extensions: List<T>,
    val failedRepoUrls: Set<String>,
) {
    fun hasCompleteListing(repoUrl: String?): Boolean =
        failedRepoUrls.isEmpty() || (repoUrl != null && repoUrl !in failedRepoUrls)
}
