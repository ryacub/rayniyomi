package eu.kanade.tachiyomi.ui.library.anime

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

class AnimeLibraryItemSearchTest {

    @BeforeEach
    fun setUp() {
        // The real getNameForAnimeInfo() extension reads SourcePreferences through Injekt and
        // computes the displayed name from the enabled languages and the source's lang/name.
        // Registering the preferences makes the extension deterministic in tests.
        Injekt.addSingleton(
            SourcePreferences(
                InMemoryPreferenceStore(
                    sequenceOf(
                        InMemoryPreferenceStore.InMemoryPreference(
                            key = "source_languages",
                            data = setOf("en"),
                            defaultValue = emptySet<String>(),
                        ),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `legacy plain term matches title case-insensitively`() {
        val item = item(anime(title = "Naruto"))
        assertTrue(item.matchesQuery("naruto"))
        assertFalse(item.matchesQuery("sakura"))
    }

    @Test
    fun `legacy plain term matches author artist and description`() {
        val item = item(
            anime(
                author = "Masashi Kishimoto",
                artist = "Kishimoto",
                description = "A ninja story",
            ),
        )
        assertTrue(item.matchesQuery("kishimoto"))
        assertTrue(item.matchesQuery("ninja story"))
        assertFalse(item.matchesQuery("pirate"))
    }

    @Test
    fun `legacy comma list requires every subconstraint to match`() {
        val item = item(anime(genre = listOf("Action", "Adventure")), sourceName = "Crunchyroll")
        assertTrue(item.matchesQuery("crunchyroll, action"))
        assertFalse(item.matchesQuery("crunchyroll, comedy"))
        assertFalse(item.matchesQuery("netflix, action"))
    }

    @Test
    fun `legacy leading minus negates a subconstraint`() {
        val item = item(anime(genre = listOf("Action", "Adventure")), sourceName = "Crunchyroll")
        assertTrue(item.matchesQuery("-comedy"))
        assertFalse(item.matchesQuery("-action"))
        assertTrue(item.matchesQuery("crunchyroll, -comedy"))
        assertFalse(item.matchesQuery("crunchyroll, -action"))
    }

    @Test
    fun `legacy id prefix matches the exact id`() {
        val item = item(anime(id = 42L))
        assertTrue(item.matchesQuery("id:42"))
        assertFalse(item.matchesQuery("id:43"))
        assertFalse(item.matchesQuery("id:abc"))
    }

    @Test
    fun `uppercase ID prefix never matches`() {
        // Preserved quirk: the prefix check is case-insensitive but the value extraction is not,
        // so "ID:42" always fails even for a matching id. This mirrors the pre-ticket behavior.
        val item = item(anime(id = 42L))
        assertFalse(item.matchesQuery("ID:42"))
    }

    @Test
    fun `legacy phrase text matches title`() {
        val item = item(anime(title = "Naruto Shippuden"))
        assertTrue(item.matchesQuery("naruto shippuden"))
    }

    @Test
    fun `routing keeps comma queries equivalent to the legacy path`() {
        val item = item(anime(genre = listOf("Action", "Adventure")), sourceName = "Crunchyroll")
        val mismatch = item(anime(title = "Sakura", genre = listOf("Romance")), sourceName = "Crunchyroll")
        for (query in listOf("naruto, action", "sakura, romance", "crunchyroll, adventure")) {
            assertEquals(item.matches(query), item.matchesQuery(query), query)
        }
        for (query in listOf("sakura, action", "naruto, comedy")) {
            assertEquals(mismatch.matches(query), mismatch.matchesQuery(query), query)
        }
    }

    @Test
    fun `field queries match each supported field and alias`() {
        val item = item(
            anime(
                title = "Naruto",
                author = "Masashi Kishimoto",
                artist = "Kishimoto",
                description = "A ninja story",
                genre = listOf("Action", "Adventure"),
            ),
            sourceName = "Crunchyroll",
        )
        assertTrue(item.matchesQuery("title:naruto"))
        assertFalse(item.matchesQuery("title:onepiece"))
        assertTrue(item.matchesQuery("author:kishimoto"))
        assertTrue(item.matchesQuery("artist:kishimoto"))
        assertTrue(item.matchesQuery("description:ninja"))
        assertTrue(item.matchesQuery("desc:ninja"))
        assertTrue(item.matchesQuery("genre:action"))
        assertTrue(item.matchesQuery("tag:adventure"))
        assertTrue(item.matchesQuery("source:crunchyroll"))
        assertTrue(item.matchesQuery("src:crunchy"))
        assertFalse(item.matchesQuery("genre:comedy"))
    }

    @Test
    fun `operators AND OR and NOT evaluate over the tree`() {
        val item = item(
            anime(
                title = "Naruto",
                genre = listOf("Action", "Adventure"),
            ),
            sourceName = "Crunchyroll",
        )
        assertTrue(item.matchesQuery("naruto && action"))
        assertFalse(item.matchesQuery("naruto && comedy"))
        assertTrue(item.matchesQuery("naruto || onepiece"))
        assertTrue(item.matchesQuery("naruto || comedy"))
        assertTrue(item.matchesQuery("-genre:comedy"))
        assertFalse(item.matchesQuery("-genre:action"))
        assertTrue(item.matchesQuery("naruto && -genre:comedy"))
    }

    @Test
    fun `parentheses group expressions`() {
        val item = item(anime(title = "Naruto", genre = listOf("Action", "Adventure")))
        assertTrue(item.matchesQuery("(naruto || onepiece) && action"))
        assertFalse(item.matchesQuery("(naruto || onepiece) && comedy"))
        assertFalse(item.matchesQuery("-(naruto || sakura)"))
        assertTrue(item.matchesQuery("-(onepiece || sakura)"))
    }

    @Test
    fun `quoted values match text with spaces`() {
        val item = item(anime(title = "One Piece"))
        assertTrue(item.matchesQuery("title:\"one piece\""))
        assertFalse(item.matchesQuery("title:\"one piece two\""))
    }

    @Test
    fun `unicode text matches in both grammars`() {
        val item = item(anime(title = "機動戦士ガンダム 水星の魔女"))
        assertTrue(item.matchesQuery("水星の魔女"))
        assertTrue(item.matchesQuery("\"水星\""))
        assertTrue(item.matchesQuery("title:水星"))
    }

    @Test
    fun `source local alias matches the local source`() {
        val localItem = item(anime(source = LocalAnimeSource.ID), sourceName = "Local Source")
        val onlineItem = item(anime(source = 5L), sourceName = "Crunchyroll")
        assertTrue(localItem.matchesQuery("source:local"))
        assertFalse(onlineItem.matchesQuery("source:local"))
    }

    @Test
    fun `empty quoted field value follows upstream empty-text semantics`() {
        // Upstream treats an empty field value as a check for an empty field, not as a hard
        // false. Pinned here so the port matches upstream exactly.
        val withTitle = item(anime(title = "Naruto"))
        val withoutTitle = item(anime(title = ""))
        assertFalse(withTitle.matchesQuery("title:\"\""))
        assertTrue(withoutTitle.matchesQuery("title:\"\""))
    }

    @Test
    fun `malformed queries never throw and produce a boolean`() {
        val item = item(anime(title = "Naruto", genre = listOf("Action")))
        for (query in listOf(
            "((((&&",
            "|||",
            "genre:",
            "a &&",
            "\"unclosed",
            "-",
            "title:",
            "a ||",
            ")))",
        )) {
            assertDoesNotThrow {
                item.matchesQuery(query)
            }
        }
    }

    @Test
    fun `unparseable expression matches everything and cannot empty the library`() {
        val item = item(anime(title = "Naruto"))
        assertTrue(item.matchesQuery("((((&&"))
    }

    private fun anime(
        title: String = "Naruto",
        author: String? = "Masashi Kishimoto",
        artist: String? = null,
        description: String? = null,
        genre: List<String>? = listOf("Action", "Adventure"),
        source: Long = 5L,
        id: Long = 42L,
    ): Anime {
        return Anime.create().copy(
            id = id,
            title = title,
            author = author,
            artist = artist,
            description = description,
            genre = genre,
            source = source,
        )
    }

    private fun item(anime: Anime, sourceName: String = "Crunchyroll"): AnimeLibraryItem {
        val source = mockk<AnimeSource>()
        every { source.name } returns sourceName
        every { source.lang } returns "en"
        val sourceManager = mockk<AnimeSourceManager>()
        every { sourceManager.getOrStub(any()) } returns source
        return AnimeLibraryItem(
            libraryAnime = LibraryAnime(
                anime = anime,
                category = 0L,
                totalCount = 10L,
                seenCount = 3L,
                bookmarkCount = 1L,
                fillermarkCount = 0L,
                latestUpload = 0L,
                episodeFetchedAt = 0L,
                lastSeen = 0L,
            ),
            sourceManager = sourceManager,
        )
    }
}
