package eu.kanade.tachiyomi.data.coil

import java.io.File
import java.net.URI
import java.net.URISyntaxException

internal fun String.toLocalImageFile(): File {
    if (!startsWith("file://")) return File(this)

    val path = removePrefix("file://")
    val decodedPath = try {
        // Decode local file URIs. Keep legacy paths with unescaped delimiters literal.
        URI(this).takeIf { it.rawPath == path }?.path
    } catch (_: URISyntaxException) {
        null
    }
    return File(decodedPath ?: path)
}
