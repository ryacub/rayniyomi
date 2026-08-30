package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.data.database.models.anime.EpisodeImpl
import eu.kanade.tachiyomi.data.database.models.anime.toDomainEpisode
import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.data.database.models.manga.toDomainChapter
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DatabaseModelConversionTest {

    @Test
    fun `episode conversion rejects missing identifiers`() {
        assertThrows<DatabaseModelConversionException> {
            EpisodeImpl().apply { anime_id = 1L }.toDomainEpisode()
        }
        assertThrows<DatabaseModelConversionException> {
            EpisodeImpl().apply { id = 1L }.toDomainEpisode()
        }
    }

    @Test
    fun `chapter conversion rejects missing identifiers`() {
        assertThrows<DatabaseModelConversionException> {
            ChapterImpl().apply { manga_id = 1L }.toDomainChapter()
        }
        assertThrows<DatabaseModelConversionException> {
            ChapterImpl().apply { id = 1L }.toDomainChapter()
        }
    }
}
