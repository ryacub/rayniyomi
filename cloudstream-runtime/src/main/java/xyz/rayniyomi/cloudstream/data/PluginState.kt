package xyz.rayniyomi.cloudstream.data

import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class Repository(val url: String, val name: String)

@Serializable
data class InstalledPlugin(
    val internalName: String,
    val name: String,
    val version: Int,
    val language: String,
    val repositoryUrl: String,
    val fileSha256: String,
    val tvTypes: List<String>,
    val authors: List<String>,
    val enabled: Boolean = true,
) {
    val isAnimeCompatible: Boolean
        get() = isAnimeCompatible(tvTypes)

    /** CloudStream marks a plugin that always updates with version -1. */
    fun hasUpdate(remote: PluginEntry): Boolean = if (remote.version == -1) {
        remote.sha256?.equals(fileSha256, ignoreCase = true) == false
    } else {
        remote.version > version
    }
}

@Serializable
data class PluginState(
    val consentGiven: Boolean = false,
    val repositories: List<Repository> = emptyList(),
    val installed: List<InstalledPlugin> = emptyList(),
) {
    fun giveConsent(): PluginState = copy(consentGiven = true)

    fun addRepository(url: String, name: String): PluginState {
        require(isHttpsUrl(url)) { "A repository URL must use HTTPS" }
        if (repositories.any { it.url == url }) return this
        return copy(repositories = repositories + Repository(url, name))
    }

    fun removeRepository(url: String): PluginState = copy(
        repositories = repositories.filterNot { it.url == url },
        installed = installed.filterNot { it.repositoryUrl == url },
    )

    fun install(entry: PluginEntry, repositoryUrl: String, fileSha256: String): PluginState {
        val record = InstalledPlugin(
            internalName = entry.internalName,
            name = entry.name,
            version = entry.version,
            language = entry.language.orEmpty(),
            repositoryUrl = repositoryUrl,
            fileSha256 = fileSha256,
            tvTypes = entry.tvTypes,
            authors = entry.authors,
            enabled = installed.find { it.internalName == entry.internalName }?.enabled ?: true,
        )
        return copy(installed = installed.filterNot { it.internalName == entry.internalName } + record)
    }

    fun setEnabled(internalName: String, enabled: Boolean): PluginState =
        copy(installed = installed.map { if (it.internalName == internalName) it.copy(enabled = enabled) else it })

    fun remove(internalName: String): PluginState = copy(
        installed = installed.filterNot {
            it.internalName ==
                internalName
        },
    )

    fun providerPlugins(): List<InstalledPlugin> =
        if (consentGiven) installed.filter { it.enabled && it.isAnimeCompatible } else emptyList()
}

object PluginStateCodec {
    fun encode(state: PluginState): String = repositoryJson.encodeToString(PluginState.serializer(), state)

    fun decode(text: String): PluginState =
        runCatching { repositoryJson.decodeFromString(PluginState.serializer(), text) }.getOrDefault(PluginState())
}

fun writeAtomically(target: File, bytes: ByteArray) {
    val temp = File(target.parentFile, "${target.name}.tmp")
    FileOutputStream(temp).use {
        it.write(bytes)
        it.fd.sync()
    }
    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
}
