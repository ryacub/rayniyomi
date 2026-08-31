package eu.kanade.tachiyomi.extension.anime.model

sealed interface AnimeLoadResult {
    data class Success(val extension: AnimeExtension.Installed) : AnimeLoadResult
    data class Untrusted(val extension: AnimeExtension.Untrusted) : AnimeLoadResult
    data class Error(val message: String) : AnimeLoadResult
}
