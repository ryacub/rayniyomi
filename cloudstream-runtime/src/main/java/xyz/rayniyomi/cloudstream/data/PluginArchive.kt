package xyz.rayniyomi.cloudstream.data

import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

sealed interface PluginArchiveCheck {
    data class Valid(val sha256: String) : PluginArchiveCheck
    data object MissingHash : PluginArchiveCheck
    data object HashMismatch : PluginArchiveCheck
    data object NotAPlugin : PluginArchiveCheck
}

fun checkPluginArchive(bytes: ByteArray, expectedSha256: String?): PluginArchiveCheck {
    expectedSha256 ?: return PluginArchiveCheck.MissingHash
    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    if (!actual.equals(expectedSha256, ignoreCase = true)) return PluginArchiveCheck.HashMismatch

    val entries = runCatching {
        ZipInputStream(bytes.inputStream()).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toSet() }
    }.getOrNull().orEmpty()
    return if ("manifest.json" in entries && "classes.dex" in entries) {
        PluginArchiveCheck.Valid(actual)
    } else {
        PluginArchiveCheck.NotAPlugin
    }
}

fun isHttpsUrl(url: String): Boolean =
    runCatching {
        URI(url)
    }.getOrNull()?.let { it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank() }
        ?: false
