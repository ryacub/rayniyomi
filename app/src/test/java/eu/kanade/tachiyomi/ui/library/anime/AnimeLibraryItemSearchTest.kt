package eu.kanade.tachiyomi.ui.library.anime

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
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
import tachiyomi.domain.library.model.search.parseSearchQuery
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.time.Instant
import java.time.ZoneId

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

    @Test
    fun `a half-typed negated group does not blank the library`() {
        val item = item(anime(title = "Naruto"))
        assertTrue(item.matchesQuery("title:naruto && -("))
        assertTrue(item.matchesQuery("title:naruto && -()"))
        assertFalse(item.matchesQuery("title:naruto && -(naruto"))
    }

    @Test
    fun `language field matches source language`() {
        val en = item(anime(), sourceLanguage = "en")
        val ja = item(anime(), sourceLanguage = "ja")
        assertTrue(en.matchesQuery("language:en"))
        assertTrue(en.matchesQuery("lang:en"))
        assertTrue(ja.matchesQuery("language:ja"))
        assertFalse(ja.matchesQuery("language:en"))
        assertFalse(en.matchesQuery("language:ja"))
    }

    @Test
    fun `empty language value checks empty source language`() {
        val empty = item(anime(), sourceLanguage = "")
        val en = item(anime(), sourceLanguage = "en")
        assertTrue(empty.matchesQuery("language:\"\""))
        assertFalse(en.matchesQuery("language:\"\""))
    }

    @Test
    fun `language and notes do not leak into general text search`() {
        // A plain-text term whose value equals a language or notes value must only
        // match through the explicit `field:` prefix, never through general search.
        // The default fixture has no "fr" in title, author, genre or source name.
        // A bare term routes to the legacy matcher, so it is evaluated directly
        // as a GeneralQueryNode rather than through matchesQuery.
        val fr = item(anime(), sourceLanguage = "fr")
        val general = parseSearchQuery("fr")
        assertFalse(general.matches(fr, ZoneId.systemDefault()))
        assertTrue(fr.matchesQuery("language:fr"))
        assertFalse(fr.matchesQuery("language:en"))
    }

    @Test
    fun `notes field never matches text and empty value matches everything`() {
        val item = item(anime())
        assertFalse(item.matchesQuery("notes:foo"))
        assertFalse(item.matchesQuery("note:foo"))
        assertTrue(item.matchesQuery("notes:\"\""))
    }

    @Test
    fun `date comparisons use the entry timestamps`() {
        val item = item(
            anime(
                dateAdded = Instant.parse("2024-06-15T12:00:00Z").toEpochMilli(),
                nextUpdate = Instant.parse("2028-03-01T12:00:00Z").toEpochMilli(),
            ),
        )
        assertTrue(item.matchesQuery("added>=2024-01-01"))
        assertTrue(item.matchesQuery("added>2024-01-01"))
        assertFalse(item.matchesQuery("added<2024-01-01"))
        assertFalse(item.matchesQuery("added>2024-12-31"))
        assertFalse(item.matchesQuery("nu<2027-01-01"))
        assertTrue(item.matchesQuery("nu>2027-12-31"))
        assertTrue(item.matchesQuery("nu>=2027-01-01"))
    }

    @Test
    fun `next update comparison excludes completed entries`() {
        val completed = item(
            anime(
                nextUpdate = Instant.parse("2028-03-01T12:00:00Z").toEpochMilli(),
                status = SAnime.COMPLETED.toLong(),
            ),
        )
        assertFalse(completed.matchesQuery("nu<2027-01-01"))
        assertFalse(completed.matchesQuery("nu>=2027-01-01"))
        assertTrue(completed.matchesQuery("-nu<2027-01-01"))
        val ongoing = item(anime(nextUpdate = Instant.parse("2028-03-01T12:00:00Z").toEpochMilli()))
        assertTrue(ongoing.matchesQuery("nu>2027-12-31"))
    }

    @Test
    fun `date comparisons respect the evaluation zone`() {
        val item = item(anime(dateAdded = Instant.parse("2019-12-31T23:00:00Z").toEpochMilli()))
        val node = parseSearchQuery("added>=2020-01-01")
        assertTrue(node.matches(item, ZoneId.of("Pacific/Kiritimati")))
        assertFalse(node.matches(item, ZoneId.of("Pacific/Niue")))
    }

    @Test
    fun `fetch interval comparisons use the absolute interval`() {
        val positive = item(anime(fetchInterval = 7))
        val negative = item(anime(fetchInterval = -7))
        val small = item(anime(fetchInterval = 3))
        assertTrue(positive.matchesQuery("fi=7"))
        assertTrue(negative.matchesQuery("fi=7"))
        assertTrue(positive.matchesQuery("fetchinterval>5"))
        assertFalse(small.matchesQuery("fi=7"))
        assertTrue(small.matchesQuery("fi<5"))
    }

    @Test
    fun `fetch interval comparisons match the zero default sentinel`() {
        val defaultInterval = item(anime(fetchInterval = 0))
        val customInterval = item(anime(fetchInterval = -7))
        assertTrue(defaultInterval.matchesQuery("fi=0"))
        assertFalse(customInterval.matchesQuery("fi=0"))
    }

    @Test
    fun `fetch interval comparisons handle the minimum integer`() {
        val minimumInterval = item(anime(fetchInterval = Int.MIN_VALUE))
        assertTrue(minimumInterval.matchesQuery("fi=2147483648"))
        assertTrue(minimumInterval.matchesQuery("fi>2147483647"))
        assertFalse(minimumInterval.matchesQuery("fi=2147483647"))
    }

    @Test
    fun `id and count comparisons use the library values`() {
        val item = item(anime(id = 42L))
        assertTrue(item.matchesQuery("id=42"))
        assertFalse(item.matchesQuery("id=43"))
        assertTrue(item.matchesQuery("unread>=1"))
        assertFalse(item.matchesQuery("unread>7"))
        assertTrue(item.matchesQuery("read=3"))
        assertFalse(item.matchesQuery("read=4"))
        assertTrue(item.matchesQuery("total>5"))
        assertFalse(item.matchesQuery("total>10"))
    }

    @Test
    fun `unread comparison uses the computed unseen count not the badge value`() {
        val item = item(anime())
        assertEquals(-1L, item.unseenCount)
        assertTrue(item.matchesQuery("unread>=1"))
    }

    @Test
    fun `malformed comparison values never match and negation flips them`() {
        val item = item(anime())
        assertFalse(item.matchesQuery("id>abc"))
        assertTrue(item.matchesQuery("-id>abc"))
        assertFalse(item.matchesQuery("added>notadate"))
        assertTrue(item.matchesQuery("-added>notadate"))
    }

    @Test
    fun `comparisons evaluate inside operator trees`() {
        val item = item(anime(title = "Naruto", id = 42L))
        assertTrue(item.matchesQuery("unread>=1 && total>5"))
        assertFalse(item.matchesQuery("unread>7 && total>5"))
        assertTrue(item.matchesQuery("unread>=1 || read=0"))
        assertTrue(item.matchesQuery("id=42 && -genre:comedy"))
        assertFalse(item.matchesQuery("id=43 && -genre:comedy"))
        assertTrue(item.matchesQuery("(total>=100 || read=3) && genre:action"))
        assertFalse(item.matchesQuery("-(unread>=1 || total>5)"))
    }

    private fun anime(
        title: String = "Naruto",
        author: String? = "Masashi Kishimoto",
        artist: String? = null,
        description: String? = null,
        genre: List<String>? = listOf("Action", "Adventure"),
        source: Long = 5L,
        id: Long = 42L,
        dateAdded: Long = 0L,
        fetchInterval: Int = 0,
        nextUpdate: Long = 0L,
        status: Long = 0L,
    ): Anime {
        return Anime.create().copy(
            id = id,
            title = title,
            author = author,
            artist = artist,
            description = description,
            genre = genre,
            source = source,
            dateAdded = dateAdded,
            fetchInterval = fetchInterval,
            nextUpdate = nextUpdate,
            status = status,
        )
    }

    // The badge-gated sourceLanguage var stays at its default: language search must work
    // through resolvedSourceLang regardless of the badge setting.
    private fun item(
        anime: Anime,
        sourceName: String = "Crunchyroll",
        sourceLanguage: String = "en",
    ): AnimeLibraryItem {
        val source = mockk<AnimeSource>()
        every { source.name } returns sourceName
        every { source.lang } returns sourceLanguage
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
