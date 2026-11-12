package eu.kanade.domain.entries.anime.interactor

import aniyomi.domain.anime.SeasonAnime
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.season.interactor.GetAnimeSeasonsByParentId
import tachiyomi.domain.items.season.interactor.ShouldUpdateDbSeason

class SyncSeasonsWithSourceTest {

    @Test
    fun `an existing season keeps its id and receives the new source memo`() = runTest {
        val parent = Anime.create().copy(id = 1, title = "Anime")
        val stored = Anime.create().copy(
            id = 2,
            parentId = 1,
            url = "season",
            title = "Anime Season 1",
            seasonNumber = 1.0,
            seasonSourceOrder = 0,
        )
        val newMemo = JsonObject(mapOf("cursor" to JsonPrimitive(7)))
        val remote = SAnime.create().apply {
            url = stored.url
            title = stored.title
            season_number = stored.seasonNumber
            memo = newMemo
        }
        val source = mockk<AnimeSource> { every { id } returns 1L }
        val updateAnime = mockk<UpdateAnime>(relaxed = true)
        val networkToLocal = mockk<NetworkToLocalAnime> {
            coEvery { await(any()) } returns stored
        }
        val existing = mockk<GetAnimeSeasonsByParentId> {
            coEvery { await(1L) } returns listOf(SeasonAnime(stored, 0, 0, 0, 0, 0, 0, 0))
        }
        val sync = SyncSeasonsWithSource(updateAnime, mockk(), networkToLocal, ShouldUpdateDbSeason(), existing)

        sync.await(listOf(remote), parent, source)

        coVerify { updateAnime.awaitAll(match { it.single().id == stored.id && it.single().memo == newMemo }) }
    }
}
