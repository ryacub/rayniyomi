package xyz.rayniyomi.cloudstream.host

import java.io.File

/** A plugin build on one app and runtime build. An update of any of the three ends a quarantine. */
internal data class PluginKey(val internalName: String, val fileSha256: String, val hostVersion: String) {
    val fileName: String get() = "$internalName-${fileSha256.take(16)}-$hostVersion"
}

internal class PluginQuarantinedException(reason: String) :
    Exception("Disabled after it crashed the app: $reason")

internal class PluginQuarantine(private val directory: File) {

    fun mark(key: PluginKey, reason: String) {
        directory.mkdirs()
        File(directory, key.fileName).writeText(reason)
    }

    fun reason(key: PluginKey): String? = File(directory, key.fileName).takeIf { it.isFile }?.readText()
}
