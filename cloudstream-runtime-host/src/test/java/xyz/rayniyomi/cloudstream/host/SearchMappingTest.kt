package xyz.rayniyomi.cloudstream.host

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSearchResponseList
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SearchMappingTest {

    private val api = TestApi()

    private fun anime(title: String, type: TvType? = TvType.Anime) =
        api.newAnimeSearchResponse(title, "https://site.test/anime/$title", type ?: TvType.Anime) {
            this.type = type
            posterUrl = "https://site.test/$title.jpg"
        }

    @Test
    fun `a search result becomes an anime with an absolute URL`() {
        val anime = anime("Naruto").toSAnime()

        anime.url shouldBe "https://site.test/anime/Naruto"
        anime.title shouldBe "Naruto"
        anime.thumbnail_url shouldBe "https://site.test/Naruto.jpg"
    }

    @Test
    fun `movie and TV results are not anime content, untyped results are`() {
        anime("A", TvType.Anime).isAnimeContent() shouldBe true
        anime("B", TvType.AnimeMovie).isAnimeContent() shouldBe true
        anime("C", TvType.OVA).isAnimeContent() shouldBe true
        anime("D", null).isAnimeContent() shouldBe true
        api.newMovieSearchResponse("E", "https://site.test/e", TvType.Movie).isAnimeContent() shouldBe false
        anime("F", TvType.TvSeries).isAnimeContent() shouldBe false
    }

    @Test
    fun `a home page flattens its lists, keeps anime only, and drops duplicates`() {
        val page = newHomePageResponse(
            listOf(
                HomePageList("One", listOf(anime("A"), anime("B"))),
                HomePageList("Two", listOf(anime("B"), anime("M", TvType.Movie), anime("C"))),
            ),
            hasNext = true,
        ).toAnimesPage()

        page.animes.map { it.title } shouldContainExactly listOf("A", "B", "C")
        page.hasNextPage shouldBe true
    }

    @Test
    fun `a missing search result is an empty last page`() {
        val page = null.toAnimesPage()

        page.animes shouldBe emptyList()
        page.hasNextPage shouldBe false
    }

    @Test
    fun `a search result list keeps its next page flag`() {
        val page = newSearchResponseList(listOf(anime("A")), hasNext = true).toAnimesPage()

        page.animes.map { it.title } shouldContainExactly listOf("A")
        page.hasNextPage shouldBe true
    }

    @Test
    fun `popular uses the first main page section and latest the second`() {
        val sections = listOf(MainPageData("Popular", "p"), MainPageData("Latest", "l"))
        val api = TestApi(mainPage = sections)

        api.popularRequest()?.name shouldBe "Popular"
        api.latestRequest()?.name shouldBe "Latest"
        api.supportsLatestPage() shouldBe true
    }

    @Test
    fun `a provider without a main page has no popular or latest page`() {
        val api = TestApi(hasMainPage = false)

        api.popularRequest() shouldBe null
        api.latestRequest() shouldBe null
        api.supportsLatestPage() shouldBe false
    }

    @Test
    fun `a provider with one section has no latest page`() {
        val api = TestApi(mainPage = listOf(MainPageData("Only", "o")))

        api.popularRequest()?.name shouldBe "Only"
        api.latestRequest() shouldBe null
        api.supportsLatestPage() shouldBe false
    }
}
