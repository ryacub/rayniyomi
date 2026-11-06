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
}
