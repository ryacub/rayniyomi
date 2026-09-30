package eu.kanade.tachiyomi.di

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.defaultJson
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import eu.kanade.tachiyomi.animesource.PreferenceScreen as AnimePreferenceScreen
import eu.kanade.tachiyomi.animesource.sourcePreferences as configurableAnimeSourcePreferences
import eu.kanade.tachiyomi.animesource.utils.sourcePreferences as animeUtilsSourcePreferences
import eu.kanade.tachiyomi.source.PreferenceScreen as MangaPreferenceScreen
import eu.kanade.tachiyomi.source.sourcePreferences as configurableMangaSourcePreferences

@RunWith(AndroidJUnit4::class)
class ExtensionInjektContractAndroidTest {

    @Test
    fun exportedExtensionLookupsResolveHostBindings() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertSame(app, Injekt.get<Application>())

        val mangaSource = MangaConfigurableProbe()
        assertSame(app.getSharedPreferences("source_101", Context.MODE_PRIVATE), mangaSource.getSourcePreferences())
        assertSame(
            app.getSharedPreferences("source_101", Context.MODE_PRIVATE),
            mangaSource.configurableMangaSourcePreferences(),
        )
        assertSame(
            app.getSharedPreferences("manga-key", Context.MODE_PRIVATE),
            configurableMangaSourcePreferences("manga-key"),
        )

        val animeSource = AnimeConfigurableProbe()
        assertSame(app.getSharedPreferences("source_202", Context.MODE_PRIVATE), animeSource.getSourcePreferences())
        assertSame(
            app.getSharedPreferences("source_202", Context.MODE_PRIVATE),
            animeSource.configurableAnimeSourcePreferences(),
        )
        assertSame(
            app.getSharedPreferences("anime-key", Context.MODE_PRIVATE),
            configurableAnimeSourcePreferences("anime-key"),
        )
        assertSame(
            app.getSharedPreferences("anime-key", Context.MODE_PRIVATE),
            animeUtilsSourcePreferences("anime-key"),
        )
        assertSame(
            app.getSharedPreferences("source_202", Context.MODE_PRIVATE),
            animeSource.animeUtilsSourcePreferences(),
        )
        assertSame(
            app.getSharedPreferences("source_303", Context.MODE_PRIVATE),
            animeUtilsSourcePreferences(303L),
        )

        val hostJson = Injekt.get<Json>()
        assertSame(hostJson, defaultJson)

        val hostNetwork = Injekt.get<NetworkHelper>()
        assertSame(hostNetwork, MangaHttpProbe().resolvedNetwork())
        assertSame(hostNetwork, AnimeHttpProbe().resolvedNetwork())
    }

    private class MangaConfigurableProbe : ConfigurableSource {
        override val id: Long = 101L
        override val name: String = "Manga probe"
        override val supportsLatest: Boolean = false

        override fun setupPreferenceScreen(screen: MangaPreferenceScreen) = Unit

        override suspend fun getPopularManga(page: Int): MangasPage = error("Not used")

        override suspend fun getLatestUpdates(page: Int): MangasPage = error("Not used")

        override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage = error(
            "Not used",
        )

        override suspend fun getMangaUpdate(
            manga: SManga,
            chapters: List<SChapter>,
            fetchDetails: Boolean,
            fetchChapters: Boolean,
        ): SMangaUpdate = error("Not used")

        override suspend fun getPageList(chapter: SChapter): List<Page> = error("Not used")
    }

    private class AnimeConfigurableProbe : ConfigurableAnimeSource {
        override val id: Long = 202L
        override val name: String = "Anime probe"

        override fun setupPreferenceScreen(screen: AnimePreferenceScreen) = Unit

        override suspend fun getSeasonList(anime: SAnime): List<SAnime> = error("Not used")
    }

    private class MangaHttpProbe : HttpSource() {
        override val baseUrl: String = "https://example.invalid"
        override val name: String = "Manga HTTP probe"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false

        fun resolvedNetwork(): NetworkHelper = network
    }

    private class AnimeHttpProbe : AnimeHttpSource() {
        override val baseUrl: String = "https://example.invalid"
        override val name: String = "Anime HTTP probe"
        override val lang: String = "en"
        override val supportsLatest: Boolean = false

        fun resolvedNetwork(): NetworkHelper = network

        override fun popularAnimeRequest(page: Int): Request = error("Not used")

        override fun popularAnimeParse(response: Response): AnimesPage = error("Not used")

        override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request = error("Not used")

        override fun searchAnimeParse(response: Response): AnimesPage = error("Not used")

        override fun latestUpdatesRequest(page: Int): Request = error("Not used")

        override fun latestUpdatesParse(response: Response): AnimesPage = error("Not used")

        override fun animeDetailsParse(response: Response): SAnime = error("Not used")

        override fun episodeListParse(response: Response): List<SEpisode> = error("Not used")

        override fun episodeVideoParse(response: Response): SEpisode = error("Not used")

        override fun seasonListParse(response: Response): List<SAnime> = error("Not used")

        override fun hosterListParse(response: Response): List<Hoster> = error("Not used")

        override fun videoListParse(response: Response, hoster: Hoster): List<Video> = error("Not used")

        override fun videoListParse(response: Response): List<Video> = error("Not used")

        override fun videoUrlParse(response: Response): String = error("Not used")
    }
}
