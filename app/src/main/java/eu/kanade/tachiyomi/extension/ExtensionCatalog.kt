package eu.kanade.tachiyomi.extension

data class ExtensionCatalog<T>(
    val extensions: List<T>,
    val allReposLoaded: Boolean,
)
