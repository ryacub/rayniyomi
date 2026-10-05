package eu.kanade.tachiyomi.ui.entries.anime

import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.library.AutoUpdatePolicy
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences.Companion.ENTRY_HAS_UNVIEWED

class AnimeScreenModelOutdatedListTest {

    @Test
    fun `a caught-up library anime past its fetch window shows the outdated hint`() {
        state().isListOutdated shouldBe true
    }

    @Test
    fun `the hint hides while a refresh runs`() {
        state().copy(isRefreshingData = true).isListOutdated shouldBe false
    }

    @Test
    fun `an anime that lists seasons shows no hint`() {
        state(anime = staleAnime().copy(fetchType = FetchType.Seasons)).isListOutdated shouldBe false
    }

    @Test
    fun `a completed anime shows no hint`() {
        state(anime = staleAnime().copy(status = SAnime.COMPLETED.toLong())).isListOutdated shouldBe false
    }

    @Test
    fun `an anime with unseen episodes shows no hint when smart update skips it`() {
        state(seen = false).isListOutdated shouldBe false
    }

    @Test
    fun `an anime without a loaded policy shows no hint`() {
        state(policy = null).isListOutdated shouldBe false
    }

    private fun state(
        anime: Anime = staleAnime(),
        seen: Boolean = true,
        policy: AutoUpdatePolicy? = AutoUpdatePolicy(
            restrictions = setOf(ENTRY_HAS_UNVIEWED),
            isInUpdateCategories = true,
            fetchWindow = WINDOW_START to WINDOW_END,
        ),
    ) = AnimeScreenModel.State.Success(
        anime = anime,
        source = mockk(),
        isFromSource = false,
        episodes = listOf(
            EpisodeList.Item(
                episode = Episode.create().copy(id = 10L, animeId = anime.id, seen = seen),
                downloadState = AnimeDownload.State.NOT_DOWNLOADED,
                downloadProgress = 0,
            ),
        ),
        seasons = emptyList(),
        autoUpdatePolicy = policy,
    )

    private fun staleAnime() = Anime.create().copy(id = 1L, favorite = true, nextUpdate = WINDOW_START - 1)

    private companion object {
        const val WINDOW_START = 1_000_000L
        const val WINDOW_END = 3_000_000L
    }
}
