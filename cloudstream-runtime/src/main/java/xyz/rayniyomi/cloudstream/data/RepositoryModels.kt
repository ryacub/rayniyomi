package xyz.rayniyomi.cloudstream.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val ANIME_TV_TYPES = setOf("Anime", "AnimeMovie", "OVA")

internal val repositoryJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

@Serializable
data class RepositoryManifest(
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val manifestVersion: Int = 1,
    val pluginLists: List<String>,
)

@Serializable
data class PluginEntry(
    val url: String,
    val status: Int = 1,
    val version: Int,
    val name: String,
    val internalName: String,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val repositoryUrl: String? = null,
    val language: String? = null,
    val tvTypes: List<String> = emptyList(),
    val iconUrl: String? = null,
    val apiVersion: Int = 1,
    val fileSize: Long? = null,
    val fileHash: String? = null,
) {
    val sha256: String?
        get() = fileHash?.removePrefix("sha256-")?.lowercase()?.takeIf { it.isNotBlank() }

    val isAnimeCompatible: Boolean
        get() = isAnimeCompatible(tvTypes)
}

internal fun isAnimeCompatible(tvTypes: List<String>): Boolean = tvTypes.any { it in ANIME_TV_TYPES }

fun parseRepositoryManifest(json: String): RepositoryManifest =
    repositoryJson.decodeFromString<RepositoryManifest>(json).also {
        require(it.pluginLists.isNotEmpty()) { "The repository lists no plugins" }
    }

fun parsePluginList(json: String): List<PluginEntry> = repositoryJson.decodeFromString(json)
