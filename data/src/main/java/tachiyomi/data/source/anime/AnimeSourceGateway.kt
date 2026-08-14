package tachiyomi.data.source.anime

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.online.ParsedAnimeHttpSource
import eu.kanade.tachiyomi.animesource.online.ResolvableAnimeSource
import eu.kanade.tachiyomi.animesource.online.UriType
import kotlinx.coroutines.CancellationException
import tachiyomi.core.common.util.lang.SourceLinkageException
import tachiyomi.core.common.util.lang.reportAsSourceFailure
import java.lang.reflect.Method

object AnimeSourceGateway {

    suspend fun popular(source: AnimeCatalogueSource, page: Int): AnimesPage =
        guard(source) { source.getPopularAnime(page) }

    suspend fun search(
        source: AnimeCatalogueSource,
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): AnimesPage = guard(source) { source.getSearchAnime(page, query, filters) }

    suspend fun latest(source: AnimeCatalogueSource, page: Int): AnimesPage =
        guard(source) { source.getLatestUpdates(page) }

    suspend fun details(source: AnimeSource, anime: SAnime): SAnime =
        guard(source) { source.getAnimeDetails(anime) }

    suspend fun episodes(source: AnimeSource, anime: SAnime): List<SEpisode> =
        guard(source) { source.getEpisodeList(anime) }

    suspend fun seasons(source: AnimeSource, anime: SAnime): List<SAnime> =
        guard(source) { source.getSeasonList(anime) }

    suspend fun hosters(source: AnimeSource, episode: SEpisode): List<Hoster> =
        guard(source) { source.getHosterList(episode) }

    suspend fun videos(source: AnimeSource, episode: SEpisode): List<Video> =
        guard(source) { source.getVideoList(episode) }

    suspend fun videos(source: AnimeSource, hoster: Hoster): List<Video> =
        guard(source) { source.getVideoList(hoster) }

    fun filters(source: AnimeCatalogueSource): AnimeFilterList = guard(source) { source.getFilterList() }

    suspend fun resolveVideo(source: AnimeHttpSource, video: Video): Video? =
        guard(source) { source.resolveVideo(video) }

    fun sortHosters(source: AnimeHttpSource, hosters: List<Hoster>): List<Hoster> =
        guard(source) { source.run { hosters.sortHosters() } }

    fun sortVideos(source: AnimeHttpSource, videos: List<Video>): List<Video> =
        guard(source) { source.run { videos.sortVideos() } }

    suspend fun videoUrl(source: AnimeHttpSource, video: Video): String =
        guard(source) { source.getVideoUrl(video) }

    fun animeUrl(source: AnimeHttpSource, anime: SAnime): String = guard(source) { source.getAnimeUrl(anime) }

    fun episodeUrl(source: AnimeHttpSource, episode: SEpisode): String = guard(source) { source.getEpisodeUrl(episode) }

    fun hasHosters(source: AnimeHttpSource): Boolean = hasHosters(source) { it.declaredMethods }

    internal fun hasHosters(source: AnimeHttpSource, declaredMethods: (Class<*>) -> Array<Method>): Boolean = guard(
        source,
    ) {
        var current: Class<in AnimeHttpSource> = source.javaClass
        while (current != ParsedAnimeHttpSource::class.java &&
            current != AnimeHttpSource::class.java &&
            current != AnimeSource::class.java
        ) {
            if (declaredMethods(current).any {
                    it.name in listOf("getHosterList", "hosterListRequest", "hosterListParse")
                }
            ) {
                return@guard true
            }
            current = current.superclass ?: return@guard false
        }
        false
    }

    fun prepareEpisode(source: AnimeHttpSource, episode: SEpisode, anime: SAnime) {
        guard(source) { source.prepareNewEpisode(episode, anime) }
    }

    fun uriType(source: ResolvableAnimeSource, uri: String): UriType = guard(source) { source.getUriType(uri) }

    suspend fun animeFromUri(source: ResolvableAnimeSource, uri: String): SAnime? =
        guard(source) { source.getAnime(uri) }

    suspend fun episodeFromUri(source: ResolvableAnimeSource, uri: String): SEpisode? =
        guard(source) { source.getEpisode(uri) }

    fun setupPreferences(source: ConfigurableAnimeSource, screen: PreferenceScreen) {
        try {
            guard(source) { source.setupPreferenceScreen(screen) }
        } catch (_: SourceLinkageException) {
            // Keep the preference screen empty when the extension cannot build it.
        }
    }

    fun displayName(source: AnimeSource): String {
        return try {
            guard(source) { source.toString() }
        } catch (_: SourceLinkageException) {
            source.javaClass.name
        }
    }

    private inline fun <T> guard(source: AnimeSource, block: () -> T): T {
        return try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: LinkageError) {
            throw error.reportAsSourceFailure { source.name }
        }
    }
}
