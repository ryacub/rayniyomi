package eu.kanade.tachiyomi.ui.recovery

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager

class RecoveryDestinationSourcesTest {

    private fun stringSet(value: Set<String>): Preference<Set<String>> =
        mockk { every { get() } returns value }

    private fun preferences(disabledIds: Set<String>) = mockk<SourcePreferences> {
        every { enabledLanguages() } returns stringSet(setOf("en"))
        every { disabledMangaSources() } returns stringSet(disabledIds)
        every { disabledAnimeSources() } returns stringSet(disabledIds)
    }

    private fun mangaSource(id: Long, lang: String) = mockk<HttpSource> {
        every { this@mockk.id } returns id
        every { this@mockk.lang } returns lang
        every { name } returns "Source $id"
    }

    private fun animeSource(id: Long, lang: String) = mockk<AnimeCatalogueSource> {
        every { this@mockk.id } returns id
        every { this@mockk.lang } returns lang
        every { name } returns "Source $id"
    }

    @Test
    fun `manga destinations leave out disabled languages and disabled sources`() = runTest {
        val sourceManager = mockk<MangaSourceManager> {
            every { getOnlineSources() } returns
                listOf(mangaSource(1, "en"), mangaSource(2, "ja"), mangaSource(3, "en"))
        }
        val backend = MangaRecoveryBackend(
            repository = mockk(),
            applier = mockk(),
            sourceManager = sourceManager,
            getManga = mockk(),
            getFavorites = mockk(),
            getChapters = mockk(),
            getTracks = mockk(),
            trackerManager = mockk(),
            coverCache = mockk(),
            sourcePreferences = preferences(disabledIds = setOf("3")),
        )

        assertEquals(listOf(1L), backend.destinationSources(emptySet()).map { it.id })
    }

    @Test
    fun `anime destinations leave out disabled languages and disabled sources`() = runTest {
        val sources = listOf(animeSource(1, "en"), animeSource(2, "ja"), animeSource(3, "en"))
        val sourceManager = object : AnimeSourceManager by mockk() {
            override fun getCatalogueSources(): List<AnimeCatalogueSource> = sources
        }
        val backend = AnimeRecoveryBackend(
            repository = mockk(),
            applier = mockk(),
            sourceManager = sourceManager,
            getAnime = mockk(),
            getFavorites = mockk(),
            getEpisodes = mockk(),
            getTracks = mockk(),
            trackerManager = mockk(),
            coverCache = mockk(),
            sourcePreferences = preferences(disabledIds = setOf("3")),
        )

        assertEquals(listOf(1L), backend.destinationSources(emptySet()).map { it.id })
    }
}
