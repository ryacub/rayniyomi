package eu.kanade.tachiyomi.extension.anime.model

sealed interface AnimeLoadResult {
    data class Success(val extension: AnimeExtension.Installed) : AnimeLoadResult
    data class Untrusted(val extension: AnimeExtension.Untrusted) : AnimeLoadResult
    data class Invalid(
        val pkgName: String,
        val name: String,
        val versionName: String,
        val versionCode: Long,
        val signatureHash: String,
        val reason: InvalidReason,
        val debugDetail: String? = null,
    ) : AnimeLoadResult
    data object Error : AnimeLoadResult
}

enum class InvalidReason {
    DENYLISTED,
    METADATA_INVALID,
    SOURCE_FACTORY_THROW,
    SOURCE_ID_THROW,
}
