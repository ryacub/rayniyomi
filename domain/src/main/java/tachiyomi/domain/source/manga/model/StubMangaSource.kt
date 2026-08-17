package tachiyomi.domain.source.manga.model

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate

@Suppress("OverridingDeprecatedMember")
class StubMangaSource(
    override val id: Long,
    override val lang: String,
    override val name: String,
) : MangaSource {

    // Only a missing name forces the id fallback. A known name must render the same as
    // HttpSource.toString(), which ignores a blank lang, or the download directory this
    // stub resolves to stops matching the one the installed source wrote.
    private val isInvalid: Boolean = name.isBlank()

    override val supportsLatest: Boolean = false

    override suspend fun getPopularManga(page: Int): MangasPage =
        throw SourceNotInstalledException()

    override suspend fun getLatestUpdates(page: Int): MangasPage =
        throw SourceNotInstalledException()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        throw SourceNotInstalledException()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = throw SourceNotInstalledException()

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        throw SourceNotInstalledException()

    override fun toString(): String =
        if (!isInvalid) "$name (${lang.uppercase()})" else id.toString()

    companion object {
        fun from(source: MangaSource): StubMangaSource {
            return StubMangaSource(id = source.id, lang = source.lang, name = source.name)
        }
    }
}

class SourceNotInstalledException : Exception()
