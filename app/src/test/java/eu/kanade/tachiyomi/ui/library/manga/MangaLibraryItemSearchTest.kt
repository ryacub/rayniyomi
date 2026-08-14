package eu.kanade.tachiyomi.ui.library.manga

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.MangaSource
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.source.local.entries.manga.LocalMangaSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

class MangaLibraryItemSearchTest {

    @BeforeEach
    fun setUp() {
        // The real getNameForMangaInfo() extension reads SourcePreferences through Injekt and
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
        val item = item(manga(title = "Naruto"))
        assertTrue(item.matchesQuery("naruto"))
        assertFalse(item.matchesQuery("sakura"))
    }

    @Test
    fun `legacy plain term matches author artist and description`() {
        val item = item(
            manga(
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
        val item = item(manga(genre = listOf("Action", "Adventure")), sourceName = "MangaDex")
        assertTrue(item.matchesQuery("mangadex, action"))
        assertFalse(item.matchesQuery("mangadex, comedy"))
        assertFalse(item.matchesQuery("webtoon, action"))
    }

    @Test
    fun `legacy leading minus negates a subconstraint`() {
        val item = item(manga(genre = listOf("Action", "Adventure")), sourceName = "MangaDex")
        assertTrue(item.matchesQuery("-comedy"))
        assertFalse(item.matchesQuery("-action"))
        assertTrue(item.matchesQuery("mangadex, -comedy"))
        assertFalse(item.matchesQuery("mangadex, -action"))
    }

    @Test
    fun `legacy id prefix matches the exact id`() {
        val item = item(manga(id = 42L))
        assertTrue(item.matchesQuery("id:42"))
        assertFalse(item.matchesQuery("id:43"))
        assertFalse(item.matchesQuery("id:abc"))
    }

    @Test
    fun `uppercase ID prefix never matches`() {
        // Preserved quirk: the prefix check is case-insensitive but the value extraction is not,
        // so "ID:42" always fails even for a matching id. This mirrors the pre-ticket behavior.
        val item = item(manga(id = 42L))
        assertFalse(item.matchesQuery("ID:42"))
    }

    @Test
    fun `legacy phrase text matches title`() {
        val item = item(manga(title = "Naruto Shippuden"))
        assertTrue(item.matchesQuery("naruto shippuden"))
    }

    @Test
    fun `routing keeps comma queries equivalent to the legacy path`() {
        val item = item(manga(genre = listOf("Action", "Adventure")), sourceName = "MangaDex")
        val mismatch = item(manga(title = "Sakura", genre = listOf("Romance")), sourceName = "MangaDex")
        for (query in listOf("naruto, action", "sakura, romance", "mangadex, adventure")) {
            assertEquals(item.matches(query), item.matchesQuery(query), query)
        }
        for (query in listOf("sakura, action", "naruto, comedy")) {
            assertEquals(mismatch.matches(query), mismatch.matchesQuery(query), query)
        }
    }

    @Test
    fun `field queries match each supported field and alias`() {
        val item = item(
            manga(
                title = "Naruto",
                author = "Masashi Kishimoto",
                artist = "Kishimoto",
                description = "A ninja story",
                genre = listOf("Action", "Adventure"),
            ),
            sourceName = "MangaDex",
        )
        assertTrue(item.matchesQuery("title:naruto"))
        assertFalse(item.matchesQuery("title:onepiece"))
        assertTrue(item.matchesQuery("author:kishimoto"))
        assertTrue(item.matchesQuery("artist:kishimoto"))
        assertTrue(item.matchesQuery("description:ninja"))
        assertTrue(item.matchesQuery("desc:ninja"))
        assertTrue(item.matchesQuery("genre:action"))
        assertTrue(item.matchesQuery("tag:adventure"))
        assertTrue(item.matchesQuery("source:mangadex"))
        assertTrue(item.matchesQuery("src:dex"))
        assertFalse(item.matchesQuery("genre:comedy"))
    }

    @Test
    fun `operators AND OR and NOT evaluate over the tree`() {
        val item = item(
            manga(
                title = "Naruto",
                genre = listOf("Action", "Adventure"),
            ),
            sourceName = "MangaDex",
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
        val item = item(manga(title = "Naruto", genre = listOf("Action", "Adventure")))
        assertTrue(item.matchesQuery("(naruto || onepiece) && action"))
        assertFalse(item.matchesQuery("(naruto || onepiece) && comedy"))
        assertFalse(item.matchesQuery("-(naruto || sakura)"))
        assertTrue(item.matchesQuery("-(onepiece || sakura)"))
    }

    @Test
    fun `quoted values match text with spaces`() {
        val item = item(manga(title = "One Piece"))
        assertTrue(item.matchesQuery("title:\"one piece\""))
        assertFalse(item.matchesQuery("title:\"one piece two\""))
    }

    @Test
    fun `unicode text matches in both grammars`() {
        val item = item(manga(title = "機動戦士ガンダム 水星の魔女"))
        assertTrue(item.matchesQuery("水星の魔女"))
        assertTrue(item.matchesQuery("\"水星\""))
        assertTrue(item.matchesQuery("title:水星"))
    }

    @Test
    fun `source local alias matches the local source`() {
        val localItem = item(manga(source = LocalMangaSource.ID), sourceName = "Local Source")
        val onlineItem = item(manga(source = 5L), sourceName = "MangaDex")
        assertTrue(localItem.matchesQuery("source:local"))
        assertFalse(onlineItem.matchesQuery("source:local"))
    }

    @Test
    fun `empty quoted field value follows upstream empty-text semantics`() {
        // Upstream treats an empty field value as a check for an empty field, not as a hard
        // false. Pinned here so the port matches upstream exactly.
        val withTitle = item(manga(title = "Naruto"))
        val withoutTitle = item(manga(title = ""))
        assertFalse(withTitle.matchesQuery("title:\"\""))
        assertTrue(withoutTitle.matchesQuery("title:\"\""))
    }

    @Test
    fun `malformed queries never throw and produce a boolean`() {
        val item = item(manga(title = "Naruto", genre = listOf("Action")))
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
        val item = item(manga(title = "Naruto"))
        assertTrue(item.matchesQuery("((((&&"))
    }

    @Test
    fun `a half-typed negated group does not blank the library`() {
        val item = item(manga(title = "Naruto"))
        assertTrue(item.matchesQuery("title:naruto && -("))
        assertTrue(item.matchesQuery("title:naruto && -()"))
        assertFalse(item.matchesQuery("title:naruto && -(naruto"))
    }

    private fun manga(
        title: String = "Naruto",
        author: String? = "Masashi Kishimoto",
        artist: String? = null,
        description: String? = null,
        genre: List<String>? = listOf("Action", "Adventure"),
        source: Long = 5L,
        id: Long = 42L,
    ): Manga {
        return Manga.create().copy(
            id = id,
            title = title,
            author = author,
            artist = artist,
            description = description,
            genre = genre,
            source = source,
        )
    }

    private fun item(manga: Manga, sourceName: String = "MangaDex"): MangaLibraryItem {
        val source = mockk<MangaSource>()
        every { source.name } returns sourceName
        every { source.lang } returns "en"
        val sourceManager = mockk<MangaSourceManager>()
        every { sourceManager.getOrStub(any()) } returns source
        return MangaLibraryItem(
            libraryManga = LibraryManga(
                manga = manga,
                category = 0L,
                totalChapters = 10L,
                readCount = 3L,
                bookmarkCount = 1L,
                latestUpload = 0L,
                chapterFetchedAt = 0L,
                lastRead = 0L,
            ),
            sourceManager = sourceManager,
        )
    }
}
