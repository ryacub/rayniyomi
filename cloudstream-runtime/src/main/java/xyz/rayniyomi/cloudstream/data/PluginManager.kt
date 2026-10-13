package xyz.rayniyomi.cloudstream.data

import java.io.File
import kotlin.coroutines.cancellation.CancellationException

data class AvailablePlugins(val repository: Repository, val entries: List<PluginEntry>, val error: String? = null)

class PluginManager(
    private val store: PluginStore,
    private val pluginDirectory: File,
    private val fetcher: Fetcher,
) {
    interface Fetcher {
        suspend fun text(url: String): String
        suspend fun bytes(url: String): ByteArray
    }

    fun giveConsent() {
        store.update { it.giveConsent() }
    }

    suspend fun addRepository(url: String): Result<Repository> = catching {
        check(store.state.value.consentGiven) { "Accept the warning before you add a repository" }
        require(isHttpsUrl(url)) { "A repository URL must use HTTPS" }
        val manifest = parseRepositoryManifest(fetcher.text(url))
        store.update { it.addRepository(url, manifest.name) }
        Repository(url, manifest.name)
    }

    suspend fun availablePlugins(): List<AvailablePlugins> = store.state.value.repositories.map { repository ->
        catching {
            parseRepositoryManifest(fetcher.text(repository.url)).pluginLists.flatMap {
                parsePluginList(fetcher.text(it))
            }
        }.fold(
            onSuccess = { AvailablePlugins(repository, it) },
            onFailure = { AvailablePlugins(repository, emptyList(), it.message ?: it.javaClass.simpleName) },
        )
    }

    suspend fun install(entry: PluginEntry, repositoryUrl: String): Result<Unit> = catching {
        require(isSafeFileName(entry.internalName)) { "${entry.internalName} is not a valid plugin name" }
        require(isHttpsUrl(entry.url)) { "${entry.name} does not download over HTTPS" }
        val bytes = fetcher.bytes(entry.url)
        val sha256 = when (val check = checkPluginArchive(bytes, entry.sha256)) {
            is PluginArchiveCheck.Valid -> check.sha256
            PluginArchiveCheck.MissingHash -> error("${entry.name} has no published file hash")
            PluginArchiveCheck.HashMismatch -> error("${entry.name} does not match its published file hash")
            PluginArchiveCheck.NotAPlugin -> error("${entry.name} is not a CloudStream plugin file")
        }
        pluginDirectory.mkdirs()
        writeAtomically(pluginFile(entry.internalName), bytes)
        store.update { it.install(entry, repositoryUrl, sha256) }
    }

    fun setEnabled(internalName: String, enabled: Boolean) {
        store.update { it.setEnabled(internalName, enabled) }
    }

    fun remove(internalName: String) {
        pluginFile(internalName).delete()
        store.update { it.remove(internalName) }
    }

    fun removeRepository(url: String) {
        store.state.value.installed.filter { it.repositoryUrl == url }.forEach { pluginFile(it.internalName).delete() }
        store.update { it.removeRepository(url) }
    }

    fun pluginFile(internalName: String): File = File(pluginDirectory, "$internalName.cs3")

    private fun isSafeFileName(name: String): Boolean = SAFE_NAME.matches(name) && ".." !in name

    private suspend fun <T> catching(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        val SAFE_NAME = Regex("[A-Za-z0-9][A-Za-z0-9 ._-]*")
    }
}
