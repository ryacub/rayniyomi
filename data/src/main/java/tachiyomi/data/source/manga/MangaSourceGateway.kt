package tachiyomi.data.source.manga

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.ResolvableSource
import eu.kanade.tachiyomi.source.online.UriType
import kotlinx.coroutines.CancellationException
import okhttp3.Response
import tachiyomi.core.common.util.lang.SourceLinkageException
import tachiyomi.core.common.util.lang.reportAsSourceFailure

object MangaSourceGateway {

    suspend fun popular(source: CatalogueSource, page: Int): MangasPage =
        guard(source) { source.getPopularManga(page) }

    suspend fun search(source: CatalogueSource, page: Int, query: String, filters: FilterList): MangasPage =
        guard(source) { source.getSearchManga(page, query, filters) }

    suspend fun latest(source: CatalogueSource, page: Int): MangasPage =
        guard(source) { source.getLatestUpdates(page) }

    suspend fun details(source: MangaSource, manga: SManga): SManga =
        guard(source) { source.getMangaDetails(manga) }

    suspend fun chapters(source: MangaSource, manga: SManga): List<SChapter> =
        guard(source) { source.getChapterList(manga) }

    suspend fun pages(source: MangaSource, chapter: SChapter): List<Page> =
        guard(source) { source.getPageList(chapter) }

    fun filters(source: CatalogueSource): FilterList = guard(source) { source.getFilterList() }

    suspend fun imageUrl(source: HttpSource, page: Page): String = guard(source) { source.getImageUrl(page) }

    suspend fun image(
        source: HttpSource,
        page: Page,
        transformUrl: (String) -> String = { it },
    ): Response {
        val originalUrl = page.imageUrl ?: return guard(source) { source.getImage(page) }
        page.imageUrl = transformUrl(originalUrl)
        return try {
            guard(source) { source.getImage(page) }
        } finally {
            page.imageUrl = originalUrl
        }
    }

    fun mangaUrl(source: HttpSource, manga: SManga): String = guard(source) { source.getMangaUrl(manga) }

    fun chapterUrl(source: HttpSource, chapter: SChapter): String = guard(source) { source.getChapterUrl(chapter) }

    fun prepareChapter(source: HttpSource, chapter: SChapter, manga: SManga) {
        guard(source) { source.prepareNewChapter(chapter, manga) }
    }

    fun uriType(source: ResolvableSource, uri: String): UriType = guard(source) { source.getUriType(uri) }

    suspend fun mangaFromUri(source: ResolvableSource, uri: String): SManga? =
        guard(source) { source.getManga(uri) }

    suspend fun chapterFromUri(source: ResolvableSource, uri: String): SChapter? =
        guard(source) { source.getChapter(uri) }

    fun setupPreferences(source: ConfigurableSource, screen: PreferenceScreen) {
        try {
            guard(source) { source.setupPreferenceScreen(screen) }
        } catch (_: SourceLinkageException) {
            // Keep the preference screen empty when the extension cannot build it.
        }
    }

    fun displayName(source: MangaSource): String {
        return try {
            guard(source) { source.toString() }
        } catch (_: SourceLinkageException) {
            source.javaClass.name
        }
    }

    private inline fun <T> guard(source: MangaSource, block: () -> T): T {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: LinkageError) {
            throw error.reportAsSourceFailure { source.name }
        }
    }
}
