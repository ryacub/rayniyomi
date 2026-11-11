package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.util.awaitSingle
import rx.Observable

/**
 * A basic interface for creating a source. It could be an online source, a local source, etc.
 */
interface AnimeSource {

    /**
     * ID for the source. Must be unique.
     */
    val id: Long

    /**
     * Name of the source.
     */
    val name: String

    val lang: String
        get() = "all"

    val supportsLatest: Boolean
        get() = false

    fun getFilterList(): AnimeFilterList = AnimeFilterList()

    suspend fun getPopularAnime(page: Int): AnimesPage = throw UnsupportedOperationException()

    suspend fun getLatestUpdates(page: Int): AnimesPage = throw UnsupportedOperationException()

    suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        throw UnsupportedOperationException()

    suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate = SAnimeEpisodeUpdate(
        anime = if (fetchDetails) getAnimeDetails(anime) else anime,
        episodes = if (fetchEpisodes) getEpisodeList(anime) else episodes,
    )

    suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate = SAnimeSeasonUpdate(
        anime = if (fetchDetails) getAnimeDetails(anime) else anime,
        seasons = if (fetchSeasons) getSeasonList(anime) else seasons,
    )

    val supportsRelatedAnime: Boolean
        get() = false

    suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> = emptyList()

    /**
     * Get the updated details for a anime.
     *
     * @since extensions-lib 1.5
     * @param anime the anime to update.
     * @return the updated anime.
     */
    @Suppress("DEPRECATION")
    suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return fetchAnimeDetails(anime).awaitSingle()
    }

    /**
     * Get all the available episodes for a anime.
     *
     * @since extensions-lib 1.5
     * @param anime the anime to update.
     * @return the episodes for the anime.
     */
    @Suppress("DEPRECATION")
    suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        return fetchEpisodeList(anime).awaitSingle()
    }

    /**
     * Get all the available seasons for an anime
     *
     * @since extensions-lib 16
     * @param anime the anime to fetch seasons for.
     * @return the anime list for the anime.
     */
    suspend fun getSeasonList(anime: SAnime): List<SAnime> = throw UnsupportedOperationException()

    /**
     * Get the list of hoster for an episode. The first hoster in the list should
     * be the preferred hoster.
     *
     * @since extensions-lib 16
     * @param episode the episode.
     * @return the hosters for the episode.
     */
    suspend fun getHosterList(episode: SEpisode): List<Hoster> = throw IllegalStateException("Not used")

    /**
     * Get the list of videos for a hoster.
     *
     * @since extensions-lib 16
     * @param hoster the hoster.
     * @return the videos for the hoster.
     */
    suspend fun getVideoList(hoster: Hoster): List<Video> = throw IllegalStateException("Not used")

    /**
     * Get the list of videos a episode has. Pages should be returned
     * in the expected order; the index is ignored.
     *
     * @since extensions-lib 1.5
     * @param episode the episode.
     * @return the videos for the episode.
     */
    @Suppress("DEPRECATION")
    suspend fun getVideoList(episode: SEpisode): List<Video> {
        return fetchVideoList(episode).awaitSingle()
    }

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getAnimeDetails"),
    )
    fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getEpisodeList"),
    )
    fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> =
        throw IllegalStateException("Not used")

    @Deprecated(
        "Use the non-RxJava API instead",
        ReplaceWith("getVideoList"),
    )
    fun fetchVideoList(episode: SEpisode): Observable<List<Video>> =
        throw IllegalStateException("Not used")
}
