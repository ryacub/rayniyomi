package eu.kanade.tachiyomi.ui.library

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import eu.kanade.tachiyomi.ui.library.anime.librarySearchMatcher as animeLibrarySearchMatcher
import eu.kanade.tachiyomi.ui.library.manga.librarySearchMatcher as mangaLibrarySearchMatcher

/**
 * Evaluates a representative search battery over 10,000 manga and 10,000 anime.
 * The counts and the total ceiling keep evaluation regressions visible.
 */
class LibrarySearchPerformanceTest {

    private val date2020 = Instant.parse("2020-01-01T00:00:00Z").toEpochMilli()
    private val date2023 = Instant.parse("2023-11-14T00:00:00Z").toEpochMilli()
    private val date2027 = Instant.parse("2027-01-13T00:00:00Z").toEpochMilli()
    private val dayMillis = 86_400_000L

    private val size = 10_000

    private val mangaItems: List<MangaLibraryItem> by lazy { buildMangaLibrary() }
    private val animeItems: List<AnimeLibraryItem> by lazy { buildAnimeLibrary() }

    @BeforeEach
    fun setUp() {
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
    fun `full search battery completes under the ceiling with correct counts`() {
        evaluateBattery(
            listOf(
                "naruto",
                "genre:action && -desc:ecchi",
                "language:en",
                "notes:\"\"",
                "added>=2020-01-01",
                "unread>=1 && total>5",
                "id>5000",
                "fi=7",
                "nu<2027-01-01",
                "(total>=100 || read=0) && -genre:comedy",
            ),
        )
    }

    private fun evaluateBattery(queries: List<String>) {
        // Warm the JIT with a query that is not in the timed battery.
        countMatches("genre:action")
        var totalNanos = 0L
        for (query in queries) {
            val start = System.nanoTime()
            val count = countMatches(query)
            val elapsed = System.nanoTime() - start
            val elapsedMs = elapsed / 1_000_000
            totalNanos += elapsed
            println("LibrarySearchPerformance: $query -> $count matches in ${elapsedMs}ms")
            assertEquals(expected(query), count, query)
        }
        val totalMs = totalNanos / 1_000_000
        val itemCount = mangaItems.size + animeItems.size
        println("LibrarySearchPerformance: total ${totalMs}ms for ${queries.size} queries over $itemCount items")
        assertTrue(totalMs < 3_000, "battery took ${totalMs}ms, ceiling is 3000ms")
    }

    private fun countMatches(query: String): Int {
        val mangaMatcher = mangaLibrarySearchMatcher(query)
        val animeMatcher = animeLibrarySearchMatcher(query)
        return mangaItems.count(mangaMatcher) + animeItems.count(animeMatcher)
    }

    private fun expected(query: String): Int = when (query) {
        "naruto" -> 200
        "genre:action && -desc:ecchi" -> 5714
        "language:en" -> 10_000
        "notes:\"\"" -> 20_000
        "added>=2020-01-01" -> mangaItems.count {
            localDate(it.libraryManga.manga.dateAdded) >=
                LocalDate.parse("2020-01-01")
        } +
            animeItems.count { localDate(it.libraryAnime.anime.dateAdded) >= LocalDate.parse("2020-01-01") }
        "unread>=1 && total>5" -> 20_000
        "id>5000" -> 10_000
        "fi=7" -> 1430
        "nu<2027-01-01" -> 18_000
        "(total>=100 || read=0) && -genre:comedy" -> 6834
        else -> error("unknown query $query")
    }

    private fun localDate(epochMillis: Long): LocalDate {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun buildMangaLibrary(): List<MangaLibraryItem> {
        val enSource = mockk<MangaSource>()
        every { enSource.name } returns "MangaDex"
        every { enSource.lang } returns "en"
        val jaSource = mockk<MangaSource>()
        every { jaSource.name } returns "MangaPlus"
        every { jaSource.lang } returns "ja"
        val sourceManager = mockk<MangaSourceManager>()
        every { sourceManager.getOrStub(EN_SOURCE_ID) } returns enSource
        every { sourceManager.getOrStub(JA_SOURCE_ID) } returns jaSource
        return (0 until size).map { i ->
            val id = i + 1L
            val manga = Manga.create().copy(
                id = id,
                title = if (i % 100 == 0) "Naruto Shippuden $i" else "Manga Series $i",
                author = "Author $i",
                artist = "Artist $i",
                description = if (i % 7 == 0) "A story about ecchi ninjas" else "A plain story",
                genre = when (i % 3) {
                    0 -> listOf("Action", "Adventure")
                    1 -> listOf("Comedy")
                    else -> listOf("Romance", "Drama")
                },
                source = if (i % 2 == 0) EN_SOURCE_ID else JA_SOURCE_ID,
                dateAdded = date2020 + (i - size / 2) * dayMillis,
                fetchInterval = (i % 14) - 7,
                nextUpdate = if (i % 10 == 0) date2027 else date2023 + (i % 1000) * dayMillis,
            )
            MangaLibraryItem(
                libraryManga = LibraryManga(
                    manga = manga,
                    category = 0L,
                    totalChapters = 100L,
                    readCount = (i % 100).toLong(),
                    bookmarkCount = 0L,
                    latestUpload = 0L,
                    chapterFetchedAt = 0L,
                    lastRead = 0L,
                ),
                sourceManager = sourceManager,
            )
        }
    }

    private fun buildAnimeLibrary(): List<AnimeLibraryItem> {
        val enSource = mockk<AnimeSource>()
        every { enSource.name } returns "Crunchyroll"
        every { enSource.lang } returns "en"
        val jaSource = mockk<AnimeSource>()
        every { jaSource.name } returns "Netflix"
        every { jaSource.lang } returns "ja"
        val sourceManager = mockk<AnimeSourceManager>()
        every { sourceManager.getOrStub(EN_SOURCE_ID) } returns enSource
        every { sourceManager.getOrStub(JA_SOURCE_ID) } returns jaSource
        return (0 until size).map { i ->
            val id = i + 1L
            val anime = Anime.create().copy(
                id = id,
                title = if (i % 100 == 0) "Naruto Shippuden $i" else "Anime Series $i",
                author = "Author $i",
                artist = "Artist $i",
                description = if (i % 7 == 0) "A story about ecchi ninjas" else "A plain story",
                genre = when (i % 3) {
                    0 -> listOf("Action", "Adventure")
                    1 -> listOf("Comedy")
                    else -> listOf("Romance", "Drama")
                },
                source = if (i % 2 == 0) EN_SOURCE_ID else JA_SOURCE_ID,
                dateAdded = date2020 + (i - size / 2) * dayMillis,
                fetchInterval = (i % 14) - 7,
                nextUpdate = if (i % 10 == 0) date2027 else date2023 + (i % 1000) * dayMillis,
            )
            AnimeLibraryItem(
                libraryAnime = LibraryAnime(
                    anime = anime,
                    category = 0L,
                    totalCount = 50L,
                    seenCount = (i % 40).toLong(),
                    bookmarkCount = 0L,
                    fillermarkCount = 0L,
                    latestUpload = 0L,
                    episodeFetchedAt = 0L,
                    lastSeen = 0L,
                ),
                sourceManager = sourceManager,
            )
        }
    }

    private companion object {
        const val EN_SOURCE_ID = 1L
        const val JA_SOURCE_ID = 2L
    }
}
