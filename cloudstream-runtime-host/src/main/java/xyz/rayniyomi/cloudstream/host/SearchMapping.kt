package xyz.rayniyomi.cloudstream.host

import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.TvType
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime

val ANIME_TYPES = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

fun SearchResponse.isAnimeContent(): Boolean = type.let { it == null || it in ANIME_TYPES }

fun SearchResponse.toSAnime(): SAnime = SAnime.create().also {
    it.url = url
    it.title = name
    it.thumbnail_url = posterUrl
}

fun HomePageResponse.toAnimesPage(): AnimesPage = items.flatMap { it.list }.toAnimesPage(hasNext)

fun SearchResponseList?.toAnimesPage(): AnimesPage = this?.items.orEmpty().toAnimesPage(this?.hasNext ?: false)

private fun List<SearchResponse>.toAnimesPage(hasNext: Boolean) = AnimesPage(
    filter { it.isAnimeContent() }.distinctBy { it.url }.map { it.toSAnime() },
    hasNext,
)

fun MainAPI.popularRequest(): MainPageRequest? = mainPageRequest(0)

fun MainAPI.latestRequest(): MainPageRequest? = mainPageRequest(1)

fun MainAPI.supportsLatestPage(): Boolean = latestRequest() != null

fun MainAPI.mainPageRequest(index: Int): MainPageRequest? =
    mainPage.getOrNull(index)
        ?.takeIf { hasMainPage }
        ?.let { MainPageRequest(it.name, it.data, it.horizontalImages) }
