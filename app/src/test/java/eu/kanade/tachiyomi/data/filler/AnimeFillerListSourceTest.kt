package eu.kanade.tachiyomi.data.filler

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.file.Files

class AnimeFillerListSourceTest {

    private val cacheDirectory = Files.createTempDirectory("anime-filler-cache").toFile()

    @AfterEach
    fun tearDown() {
        cacheDirectory.deleteRecursively()
    }

    @Test
    fun `unique normalized title parses only pure filler rows`() = runTest {
        var requestedDetail = false
        val source = AnimeFillerListSource(
            cache = AnimeFillerDiskCache(cacheDirectory),
            fetchHtml = { url ->
                if (url.endsWith("/shows")) {
                    """
                    <div id="ShowList">
                        <a href="/shows/naruto">Naruto</a>
                        <a href="/shows/bleach">Bleach</a>
                    </div>
                    """.trimIndent()
                } else {
                    requestedDetail = true
                    """
                    <span property="dc:title" content="Naruto"></span>
                    <table class="EpisodeList"><tbody>
                        <tr class="manga_canon"><td class="Number">1</td><td class="Type">Manga Canon</td></tr>
                        <tr class="filler"><td class="Number">2</td><td class="Type">Filler</td></tr>
                        <tr class="mixed_canon/filler"><td class="Number">3</td><td class="Type">Mixed Canon/Filler</td></tr>
                    </tbody></table>
                    """.trimIndent()
                }
            },
        )

        assertEquals(setOf(2.0), source.getFillerEpisodes("  Naruto! "))
        assertEquals(true, requestedDetail)
    }

    @Test
    fun `fresh title scoped cache skips network until ttl expires`() = runTest {
        var now = 1_000L
        var requestCount = 0
        val source = AnimeFillerListSource(
            cache = AnimeFillerDiskCache(cacheDirectory),
            nowProvider = { now },
            fetchHtml = { url ->
                requestCount++
                if (url.endsWith("/shows")) {
                    "<div id=\"ShowList\"><a href=\"/shows/naruto\">Naruto</a></div>"
                } else {
                    "<span property=\"dc:title\" content=\"Naruto\"></span><table class=\"EpisodeList\"><tr class=\"filler\"><td class=\"Number\">2</td></tr></table>"
                }
            },
        )

        source.getFillerEpisodes("Naruto")
        source.getFillerEpisodes("Naruto")
        assertEquals(2, requestCount)

        now += AnimeFillerDiskCache.TTL_MS + 1
        source.getFillerEpisodes("Naruto")
        assertEquals(3, requestCount)
    }

    @Test
    fun `unmatched or ambiguous lookup writes nothing`() = runTest {
        var detailRequestCount = 0
        val source = AnimeFillerListSource(
            cache = AnimeFillerDiskCache(cacheDirectory),
            fetchHtml = { url ->
                if (url.endsWith("/shows")) {
                    """
                    <div id="ShowList">
                        <a href="/shows/naruto">Naruto</a>
                        <a href="/shows/naruto-shippuden">Naruto</a>
                    </div>
                    """.trimIndent()
                } else {
                    detailRequestCount++
                    ""
                }
            },
        )

        assertNull(source.getFillerEpisodes("Naruto"))
        assertEquals(0, detailRequestCount)
        assertNull(source.getFillerEpisodes("Naruto"))
        assertEquals(0, detailRequestCount)
    }
}
