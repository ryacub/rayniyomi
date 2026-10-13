package xyz.rayniyomi.cloudstream.host

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipFile

class IsolatedLoadResult<T>(val loaded: List<T>, val failures: List<Pair<String, Throwable>>)

fun <P, T> loadIsolated(items: List<P>, name: (P) -> String, load: (P) -> List<T>): IsolatedLoadResult<T> {
    val loaded = mutableListOf<T>()
    val failures = mutableListOf<Pair<String, Throwable>>()
    items.forEach { item ->
        try {
            loaded += load(item)
        } catch (e: Throwable) {
            failures += name(item) to e
        }
    }
    return IsolatedLoadResult(loaded, failures)
}

@Serializable
data class Cs3Manifest(
    val pluginClassName: String,
    val name: String? = null,
    val version: Int? = null,
    val requiresResources: Boolean = false,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun read(file: File): Cs3Manifest {
            val text = ZipFile(file).use { zip ->
                val entry = requireNotNull(zip.getEntry("manifest.json")) { "${file.name} has no manifest.json" }
                zip.getInputStream(entry).bufferedReader().use { it.readText() }
            }
            val manifest = json.decodeFromString<Cs3Manifest>(text)
            require(manifest.pluginClassName.isNotBlank()) { "${file.name} has no plugin class" }
            return manifest
        }
    }
}
