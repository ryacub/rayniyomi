package eu.kanade.tachiyomi.extension

internal data class ExtensionCatalog<T>(
    val extensions: List<T>,
    val allReposLoaded: Boolean,
)
