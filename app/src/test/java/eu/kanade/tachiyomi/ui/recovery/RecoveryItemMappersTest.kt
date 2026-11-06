package eu.kanade.tachiyomi.ui.recovery

import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.source.model.SChapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecoveryItemMappersTest {

    @Test
    fun `source chapters without a number get one from their name and keep list order`() {
        val chapters = listOf(
            SChapter.create().apply {
                url = "/c/2"
                name = "Chapter 2"
                chapter_number = -1f
            },
            SChapter.create().apply {
                url = "/c/1"
                name = "Chapter 1"
                chapter_number = 1f
            },
        )

        val items = chapters.toRecoveryItems(mangaTitle = "Title")

        assertEquals(listOf("/c/2" to 2.0, "/c/1" to 1.0), items.map { it.url to it.number })
        assertEquals(listOf(0L, 1L), items.map { it.sourceOrder })
    }

    @Test
    fun `source episodes without a number get one from their name and keep list order`() {
        val episodes = listOf(
            SEpisode.create().apply {
                url = "/e/3"
                name = "Episode 3"
                episode_number = -1f
            },
        )

        val items = episodes.toRecoveryItems(animeTitle = "Title")

        assertEquals(listOf("/e/3" to 3.0), items.map { it.url to it.number })
        assertEquals(listOf(0L), items.map { it.sourceOrder })
    }

    @Test
    fun `a chapter URL listed twice keeps only its first listing`() {
        val chapters = listOf("/c/1" to "Chapter 1", "/c/1" to "Chapter 1 again", "/c/2" to "Chapter 2").map {
            SChapter.create().apply {
                url = it.first
                name = it.second
            }
        }

        val items = chapters.toRecoveryItems(mangaTitle = "Title")

        assertEquals(listOf("/c/1" to "Chapter 1", "/c/2" to "Chapter 2"), items.map { it.url to it.name })
        assertEquals(listOf(0L, 1L), items.map { it.sourceOrder })
    }

    @Test
    fun `an episode URL listed twice keeps only its first listing`() {
        val episodes = listOf("/e/1" to "Episode 1", "/e/1" to "Episode 1 again", "/e/2" to "Episode 2").map {
            SEpisode.create().apply {
                url = it.first
                name = it.second
            }
        }

        val items = episodes.toRecoveryItems(animeTitle = "Title")

        assertEquals(listOf("/e/1" to "Episode 1", "/e/2" to "Episode 2"), items.map { it.url to it.name })
        assertEquals(listOf(0L, 1L), items.map { it.sourceOrder })
    }
}
