package tachiyomi.domain.entries.manga.model

data class MangaSourceLink(
    val firstMangaId: Long,
    val secondMangaId: Long,
)

data class MangaSourceIdentity(
    val sourceId: Long,
    val url: String,
)

enum class LinkMangaSourcesResult {
    Linked,
    AlreadyLinked,
    Conflict,
    Invalid,
}

enum class RestoreMangaSourceLinkResult {
    Linked,
    AlreadyLinked,
    MissingEndpoint,
    AmbiguousEndpoint,
    Conflict,
}

data class MangaSourceLinkWithManga(
    val link: MangaSourceLink,
    val firstManga: Manga,
    val secondManga: Manga,
)
