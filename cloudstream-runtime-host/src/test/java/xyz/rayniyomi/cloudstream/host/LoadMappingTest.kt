package xyz.rayniyomi.cloudstream.host

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import eu.kanade.tachiyomi.animesource.model.SAnime
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class LoadMappingTest {

    private val api = TestApi()

    private fun episode(data: String, number: Int?, name: String? = null, date: Long? = null) =
        api.newEpisode("https://site.test/$data", {
            episode = number
            this.name = name
            this.date = date
        })

    @Test
    fun `details map title, plot, tags, status, and poster`() = runTest {
        val response = api.newTvSeriesLoadResponse("Naruto", "https://site.test/n", TvType.Anime, emptyList()) {
            plot = "A ninja."
            tags = listOf("Action", "Shounen")
            showStatus = ShowStatus.Completed
            posterUrl = "https://site.test/n.jpg"
        }

        val anime = response.toSAnime()

        anime.title shouldBe "Naruto"
        anime.description shouldBe "A ninja."
        anime.genre shouldBe "Action, Shounen"
        anime.status shouldBe SAnime.COMPLETED
        anime.thumbnail_url shouldBe "https://site.test/n.jpg"
        anime.initialized shouldBe true
    }

    @Test
    fun `ongoing and unknown status map to the host status`() = runTest {
        val ongoing = api.newTvSeriesLoadResponse("A", "https://site.test/a", TvType.Anime, emptyList()) {
            showStatus = ShowStatus.Ongoing
        }
        val unknown = api.newTvSeriesLoadResponse("B", "https://site.test/b", TvType.Anime, emptyList())

        ongoing.toSAnime().status shouldBe SAnime.ONGOING
        unknown.toSAnime().status shouldBe SAnime.UNKNOWN
    }

    @Test
    fun `series episodes are numbered in source order and listed newest first`() = runTest {
        val response = api.newTvSeriesLoadResponse(
            "A",
            "https://site.test/a",
            TvType.Anime,
            listOf(episode("e1", 1, date = 100), episode("e2", null), episode("e3", 3, name = "Finale")),
        )

        val episodes = response.toEpisodes()

        episodes.map { it.episode_number } shouldContainExactly listOf(3f, 2f, 1f)
        episodes.map { it.url } shouldContainExactly
            listOf("https://site.test/e3", "https://site.test/e2", "https://site.test/e1")
        episodes.map { it.name } shouldContainExactly listOf("Finale", "Episode 2", "Episode 1")
        episodes.last().date_upload shouldBe 100L
    }

    @Test
    fun `anime episodes list subbed then dubbed and drop shared links`() = runTest {
        val response = api.newAnimeLoadResponse("A", "https://site.test/a", TvType.Anime) {
            addEpisodes(DubStatus.Subbed, listOf(episode("s1", 1), episode("s2", 2)))
            addEpisodes(DubStatus.Dubbed, listOf(episode("d1", 1), episode("s2", 2)))
        }

        val episodes = response.toEpisodes()

        episodes.map { it.url } shouldContainExactly
            listOf("https://site.test/d1", "https://site.test/s2", "https://site.test/s1")
        episodes.map { it.scanlator } shouldContainExactly listOf("Dubbed", "Subbed", "Subbed")
    }

    @Test
    fun `a movie is one episode that carries its data`() = runTest {
        val response = api.newMovieLoadResponse("Film", "https://site.test/f", TvType.AnimeMovie, "movie-data")

        val episodes = response.toEpisodes()

        episodes.map { it.url } shouldContainExactly listOf("movie-data")
        episodes.single().episode_number shouldBe 1f
        episodes.single().name shouldBe "Film"
    }

    @Test
    fun `a live stream is not supported`() = runTest {
        val response = api.newLiveStreamLoadResponse("Live", "https://site.test/live", "live-data")

        shouldThrow<UnsupportedOperationException> { response.toEpisodes() }
    }
}
