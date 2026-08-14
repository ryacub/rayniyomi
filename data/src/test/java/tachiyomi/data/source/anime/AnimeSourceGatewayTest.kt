package tachiyomi.data.source.anime

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.animesource.online.ResolvableAnimeSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import tachiyomi.core.common.util.lang.SourceLinkageException
import tachiyomi.core.common.util.lang.SourceLinkageReporter

class AnimeSourceGatewayTest {

    @AfterEach
    fun tearDown() {
        SourceLinkageReporter.onFailure = {}
    }

    @TestFactory
    fun `each entry point reports a LinkageError with the source name`(): List<DynamicTest> {
        val anime = SAnime.create()
        val episode = SEpisode.create()
        val hoster = Hoster("Hoster", "https://example.invalid")
        val video = Video("https://example.invalid")

        return listOf(
            failingCall<AnimeCatalogueSource>("popular") {
                coEvery { getPopularAnime(any()) } throws linkageError()
                AnimeSourceGateway.popular(this, 1)
            },
            failingCall<AnimeCatalogueSource>("search") {
                coEvery { getSearchAnime(any(), any(), any()) } throws linkageError()
                AnimeSourceGateway.search(this, 1, "query", AnimeFilterList())
            },
            failingCall<AnimeCatalogueSource>("latest") {
                coEvery { getLatestUpdates(any()) } throws linkageError()
                AnimeSourceGateway.latest(this, 1)
            },
            failingCall<AnimeSource>("details") {
                coEvery { getAnimeDetails(any()) } throws linkageError()
                AnimeSourceGateway.details(this, anime)
            },
            failingCall<AnimeSource>("episodes") {
                coEvery { getEpisodeList(any()) } throws linkageError()
                AnimeSourceGateway.episodes(this, anime)
            },
            failingCall<AnimeSource>("seasons") {
                coEvery { getSeasonList(any()) } throws linkageError()
                AnimeSourceGateway.seasons(this, anime)
            },
            failingCall<AnimeSource>("hosters") {
                coEvery { getHosterList(any()) } throws linkageError()
                AnimeSourceGateway.hosters(this, episode)
            },
            failingCall<AnimeSource>("videos for episode") {
                coEvery { getVideoList(any<SEpisode>()) } throws linkageError()
                AnimeSourceGateway.videos(this, episode)
            },
            failingCall<AnimeSource>("videos for hoster") {
                coEvery { getVideoList(any<Hoster>()) } throws linkageError()
                AnimeSourceGateway.videos(this, hoster)
            },
            failingCall<AnimeCatalogueSource>("filters") {
                every { getFilterList() } throws linkageError()
                AnimeSourceGateway.filters(this)
            },
            failingCall<AnimeHttpSource>("resolve video") {
                coEvery { resolveVideo(any()) } throws linkageError()
                AnimeSourceGateway.resolveVideo(this, video)
            },
            failingCall<AnimeHttpSource>("sort hosters") {
                every { with(this@failingCall) { any<List<Hoster>>().sortHosters() } } throws linkageError()
                AnimeSourceGateway.sortHosters(this, listOf(hoster))
            },
            failingCall<AnimeHttpSource>("sort videos") {
                every { with(this@failingCall) { any<List<Video>>().sortVideos() } } throws linkageError()
                AnimeSourceGateway.sortVideos(this, listOf(video))
            },
            failingCall<AnimeHttpSource>("video URL") {
                coEvery { getVideoUrl(any()) } throws linkageError()
                AnimeSourceGateway.videoUrl(this, video)
            },
            failingCall<AnimeHttpSource>("anime URL") {
                every { getAnimeUrl(any()) } throws linkageError()
                AnimeSourceGateway.animeUrl(this, anime)
            },
            failingCall<AnimeHttpSource>("episode URL") {
                every { getEpisodeUrl(any()) } throws linkageError()
                AnimeSourceGateway.episodeUrl(this, episode)
            },
            failingCall<AnimeHttpSource>("prepare episode") {
                every { prepareNewEpisode(any(), any()) } throws linkageError()
                AnimeSourceGateway.prepareEpisode(this, episode, anime)
            },
            failingCall<ResolvableAnimeSource>("URI type") {
                every { getUriType(any()) } throws linkageError()
                AnimeSourceGateway.uriType(this, "https://example.invalid")
            },
            failingCall<ResolvableAnimeSource>("anime from URI") {
                coEvery { getAnime(any()) } throws linkageError()
                AnimeSourceGateway.animeFromUri(this, "https://example.invalid")
            },
            failingCall<ResolvableAnimeSource>("episode from URI") {
                coEvery { getEpisode(any()) } throws linkageError()
                AnimeSourceGateway.episodeFromUri(this, "https://example.invalid")
            },
        ).map { (name, test) -> DynamicTest.dynamicTest(name) { runBlocking { test() } } }
    }

    @Test
    fun `preference screen reports a LinkageError and stays empty`() {
        val source = mockk<ConfigurableAnimeSource> {
            every { name } returns "Broken Anime Source"
            every { setupPreferenceScreen(any()) } throws linkageError()
        }
        val reported = mutableListOf<SourceLinkageException>()
        SourceLinkageReporter.onFailure = { reported += it }

        AnimeSourceGateway.setupPreferences(source, mockk<PreferenceScreen>())

        reported.single().sourceName shouldBe "Broken Anime Source"
    }

    @Test
    fun `display name reports a LinkageError and returns a class fallback`() {
        val source = object : AnimeSource {
            override val id = 1L
            override val name = "Broken Anime Source"

            override suspend fun getSeasonList(anime: SAnime): List<SAnime> = emptyList()

            override fun toString(): String = throw linkageError()
        }
        val reported = mutableListOf<SourceLinkageException>()
        SourceLinkageReporter.onFailure = { reported += it }

        AnimeSourceGateway.displayName(source) shouldBe source.javaClass.name

        reported.single().sourceName shouldBe "Broken Anime Source"
    }

    @Test
    fun `cancellation leaves the gateway without a report`() {
        val source = mockk<AnimeSource> {
            every { name } returns "Example"
            coEvery { getEpisodeList(any()) } throws CancellationException("cancelled")
        }
        val reported = mutableListOf<SourceLinkageException>()
        SourceLinkageReporter.onFailure = { reported += it }

        shouldThrow<CancellationException> {
            runBlocking { AnimeSourceGateway.episodes(source, SAnime.create()) }
        }

        reported shouldBe emptyList()
    }

    @Test
    fun `hoster capability reports a linkage fault during method inspection`() {
        val source = mockk<AnimeHttpSource> {
            every { name } returns "Broken Anime Source"
        }
        val reported = mutableListOf<SourceLinkageException>()
        SourceLinkageReporter.onFailure = { reported += it }

        val failure = shouldThrow<SourceLinkageException> {
            AnimeSourceGateway.hasHosters(source) { throw NoClassDefFoundError("MissingHoster") }
        }

        failure.sourceName shouldBe "Broken Anime Source"
        reported shouldBe listOf(failure)
    }

    private inline fun <reified T : AnimeSource> failingCall(
        name: String,
        crossinline call: suspend T.() -> Unit,
    ): Pair<String, suspend () -> Unit> {
        return name to {
            val source = mockk<T> {
                every { this@mockk.name } returns "Broken Anime Source"
            }
            val reported = mutableListOf<SourceLinkageException>()
            SourceLinkageReporter.onFailure = { reported += it }

            val failure = shouldThrow<SourceLinkageException> { source.call() }

            failure.sourceName shouldBe "Broken Anime Source"
            failure.cause.shouldBeInstanceOf<NoSuchMethodError>()
            reported shouldBe listOf(failure)
        }
    }

    private fun linkageError() = NoSuchMethodError("runBlockingK\$default")
}
