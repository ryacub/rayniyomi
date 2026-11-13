package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.data.database.models.anime.EpisodeImpl
import eu.kanade.tachiyomi.data.database.models.anime.toDomainEpisode
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DatabaseModelConversionTest {

    @Test
    fun `episode conversion rejects missing parent identifier`() {
        assertThrows<DatabaseModelConversionException> {
            EpisodeImpl(id = 1L).toDomainEpisode()
        }
    }
}
