package eu.kanade.tachiyomi.data.database.models

import eu.kanade.domain.items.chapter.model.toDbChapter
import eu.kanade.domain.items.episode.model.toDbEpisode
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryAnimeDb
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryMangaDb
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.data.items.chapter.ChapterRepositoryImpl
import tachiyomi.data.items.episode.EpisodeRepositoryImpl
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.episode.model.Episode

class PersistedEntityIdTest {

    @Test
    fun `inserting an episode returns an adapter with the generated id`() = runTest {
        InMemoryAnimeDb().use { db ->
            db.insertAnime(id = 1L, url = "anime", title = "Anime")

            val episode = Episode.create().copy(
                animeId = 1L,
                url = "episode",
                name = "Episode",
            )
            val persisted = EpisodeRepositoryImpl(db.handler)
                .addAllEpisodes(listOf(episode))
                .single()
                .toDbEpisode()

            val persistedId: Long = persisted.id
            assertTrue(persistedId > 0L)
        }
    }

    @Test
    fun `inserting a chapter returns an adapter with the generated id`() = runTest {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1L, url = "manga", title = "Manga")

            val chapter = Chapter.create().copy(
                mangaId = 1L,
                url = "chapter",
                name = "Chapter",
            )
            val persisted = ChapterRepositoryImpl(db.handler)
                .addAllChapters(listOf(chapter))
                .single()
                .toDbChapter()

            val persistedId: Long = persisted.id
            assertTrue(persistedId > 0L)
        }
    }
}
