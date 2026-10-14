package mihon.domain.extensionrepo

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val repoRegex = """^https://.*/(index\.min\.json|index\.pb|repo\.json)$""".toRegex()

internal fun String.toExtensionRepoBaseUrl(): String? {
    val formattedUrl = toHttpUrlOrNull()
        ?.toString()
        ?.takeIf { it.matches(repoRegex) }
        ?: return null

    return formattedUrl.substringBeforeLast('/')
}
