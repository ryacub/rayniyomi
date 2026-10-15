package xyz.rayniyomi.cloudstream.host

import android.content.Intent
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.app
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import okhttp3.Response
import xyz.rayniyomi.cloudstream.contract.PluginContract

internal class CloudStreamAnimeSource(
    private val api: MainAPI,
    private val plugin: PluginRow,
    private val runtimePackage: String,
) : AnimeHttpSource(), ConfigurableAnimeSource {

    override val name: String = api.name
    override val lang: String = api.lang
    override val baseUrl: String = api.mainUrl
    override val supportsLatest: Boolean = api.supportsLatestPage()
    override val id: Long = CloudStreamSourceId.of(plugin.internalName, api.name, api.lang)

    private val sections = if (api.hasMainPage) api.mainPage.map { it.name } else emptyList()

    override suspend fun getPopularAnime(page: Int): AnimesPage = mainPage(page, api.popularRequest())

    override suspend fun getLatestUpdates(page: Int): AnimesPage = mainPage(page, api.latestRequest())

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        if (query.isBlank()) {
            val section = filters.filterIsInstance<SectionFilter>().firstOrNull()?.state ?: 0
            return mainPage(page, api.mainPageRequest(section))
        }
        return guardPluginCall(name) { api.search(query, page) }.toAnimesPage()
    }

    override fun getFilterList(): AnimeFilterList =
        if (sections.isEmpty()) AnimeFilterList() else AnimeFilterList(SectionFilter(sections.toTypedArray()))

    override suspend fun getAnimeDetails(anime: SAnime): SAnime = load(anime.url).toSAnime()

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = load(anime.url).toEpisodes()

    override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()

    override suspend fun getHosterList(episode: SEpisode): List<Hoster> {
        val collector = LinkCollector()
        guardLinkLoading(name, collector::hasLinks) {
            withTimeoutOrNull(LOAD_LINKS_TIMEOUT_MS) {
                api.loadLinks(episode.url, false, collector::addSubtitle, collector::addLink)
            }
        }?.let { Log.w(TAG, "Keeping the links emitted before the error", it) }
        return buildHosters(collector.links(), collector.subtitles(), app.defaultHeaders[USER_AGENT_KEY])
    }

    override suspend fun getVideoList(hoster: Hoster): List<Video> = hoster.videoList.orEmpty()

    override fun getAnimeUrl(anime: SAnime): String = anime.url

    override fun getEpisodeUrl(episode: SEpisode): String = episode.url

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context
        Preference(context).apply {
            key = "cloudstream_manage_plugins"
            title = "Manage CloudStream plugins"
            summary = "${plugin.name} ${plugin.version} from ${plugin.repositoryUrl}"
            setOnPreferenceClickListener {
                context.startActivity(
                    Intent().setClassName(runtimePackage, PluginContract.MANAGER_ACTIVITY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }
        }.also(screen::addPreference)
    }

    private suspend fun mainPage(page: Int, request: MainPageRequest?): AnimesPage {
        request ?: return AnimesPage(emptyList(), false)
        return guardPluginCall(name) { api.getMainPage(page, request) }?.toAnimesPage()
            ?: AnimesPage(emptyList(), false)
    }

    private suspend fun load(url: String): LoadResponse =
        guardPluginCall(name) { api.load(url) }
            ?: throw PluginCallException("$name: no result for $url", NoSuchElementException(url))

    private class SectionFilter(sections: Array<String>) : AnimeFilter.Select<String>("Section", sections)

    override fun popularAnimeRequest(page: Int): Request = unused()
    override fun popularAnimeParse(response: Response): AnimesPage = unused()
    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = unused()
    override fun searchAnimeParse(response: Response): AnimesPage = unused()
    override fun latestUpdatesRequest(page: Int): Request = unused()
    override fun latestUpdatesParse(response: Response): AnimesPage = unused()
    override fun animeDetailsParse(response: Response): SAnime = unused()
    override fun episodeListParse(response: Response): List<SEpisode> = unused()
    override fun episodeVideoParse(response: Response): SEpisode = unused()
    override fun seasonListParse(response: Response): List<SAnime> = unused()
    override fun hosterListParse(response: Response): List<Hoster> = unused()
    override fun videoListParse(response: Response, hoster: Hoster): List<Video> = unused()
    override fun videoListParse(response: Response): List<Video> = unused()
    override fun videoUrlParse(response: Response): String = unused()

    private fun unused(): Nothing = throw UnsupportedOperationException("CloudStream sources do not use HTTP parsing")

    private companion object {
        const val LOAD_LINKS_TIMEOUT_MS = 60_000L
        const val TAG = "CloudStreamAnimeSource"

        const val USER_AGENT_KEY = "user-agent"
    }
}
