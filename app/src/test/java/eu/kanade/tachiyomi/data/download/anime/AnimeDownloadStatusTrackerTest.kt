package eu.kanade.tachiyomi.data.download.anime

import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownloadStatusTracker
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

class AnimeDownloadStatusTrackerTest {

    @Test
    fun `should mark stalled after threshold without retry`() {
        val download = createDownload().apply {
            status = AnimeDownload.State.DOWNLOADING
            displayStatus = AnimeDownload.DisplayStatus.DOWNLOADING
            lastProgressAt = 1_000L
            retryAttempt = 0
        }

        AnimeDownloadStatusTracker.shouldMarkStalled(download, 11_001L) shouldBe true
    }

    @Test
    fun `should not mark stalled during retry`() {
        val download = createDownload().apply {
            status = AnimeDownload.State.DOWNLOADING
            displayStatus = AnimeDownload.DisplayStatus.RETRYING
            lastProgressAt = 1_000L
            retryAttempt = 1
        }

        AnimeDownloadStatusTracker.shouldMarkStalled(download, 20_000L) shouldBe false
    }

    @Test
    fun `summarize should count downloading waiting and stalled statuses`() {
        val downloads = listOf(
            createDownload().apply { displayStatus = AnimeDownload.DisplayStatus.DOWNLOADING },
            createDownload().apply { displayStatus = AnimeDownload.DisplayStatus.DOWNLOADING },
            createDownload().apply { displayStatus = AnimeDownload.DisplayStatus.WAITING_FOR_SLOT },
            createDownload().apply { displayStatus = AnimeDownload.DisplayStatus.STALLED },
        )

        val summary = AnimeDownloadStatusTracker.summarize(downloads)

        summary.downloading shouldBe 2
        summary.waitingForSlot shouldBe 1
        summary.stalled shouldBe 1
    }

    private fun createDownload(): AnimeDownload {
        val source = mockk<AnimeHttpSource>(relaxed = true)
        val anime = mockk<Anime>(relaxed = true)
        val episode = mockk<Episode>(relaxed = true)
        every { episode.id } returns 1L
        return AnimeDownload(
            source = source,
            anime = anime,
            episode = episode,
        )
    }
}
