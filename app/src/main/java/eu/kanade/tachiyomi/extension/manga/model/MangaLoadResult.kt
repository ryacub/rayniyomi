package eu.kanade.tachiyomi.extension.manga.model

sealed interface MangaLoadResult {
    data class Success(val extension: MangaExtension.Installed) : MangaLoadResult
    data class Untrusted(val extension: MangaExtension.Untrusted) : MangaLoadResult
    data class Invalid(
        val pkgName: String,
        val name: String,
        val versionName: String,
        val versionCode: Long,
        val signatureHash: String,
        val reason: InvalidReason,
        val debugDetail: String? = null,
    ) : MangaLoadResult
    data object Error : MangaLoadResult
}

enum class InvalidReason {
    DENYLISTED,
    METADATA_INVALID,
    SOURCE_FACTORY_THROW,
    SOURCE_ID_THROW,
}
