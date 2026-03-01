package eu.kanade.tachiyomi.data.download.manga

import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownloadStatusTracker
import eu.kanade.tachiyomi.source.online.HttpSource
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

class MangaDownloadStatusTrackerTest {

    @Test
    fun `should mark stalled after threshold without retry`() {
        val download = createDownload().apply {
            status = MangaDownload.State.DOWNLOADING
            displayStatus = MangaDownload.DisplayStatus.DOWNLOADING
            lastProgressAt = 1_000L
            retryAttempt = 0
        }

        MangaDownloadStatusTracker.shouldMarkStalled(download, 11_001L) shouldBe true
    }

    @Test
    fun `should not mark stalled during retry`() {
        val download = createDownload().apply {
            status = MangaDownload.State.DOWNLOADING
            displayStatus = MangaDownload.DisplayStatus.RETRYING
            lastProgressAt = 1_000L
            retryAttempt = 1
        }

        MangaDownloadStatusTracker.shouldMarkStalled(download, 20_000L) shouldBe false
    }

    @Test
    fun `summarize should count downloading waiting and stalled statuses`() {
        val downloads = listOf(
            createDownload().apply { displayStatus = MangaDownload.DisplayStatus.DOWNLOADING },
            createDownload().apply { displayStatus = MangaDownload.DisplayStatus.DOWNLOADING },
            createDownload().apply { displayStatus = MangaDownload.DisplayStatus.WAITING_FOR_SLOT },
            createDownload().apply { displayStatus = MangaDownload.DisplayStatus.STALLED },
        )

        val summary = MangaDownloadStatusTracker.summarize(downloads)

        summary.downloading shouldBe 2
        summary.waitingForSlot shouldBe 1
        summary.stalled shouldBe 1
    }

    private fun createDownload(): MangaDownload {
        val source = mockk<HttpSource>(relaxed = true)
        val manga = mockk<Manga>(relaxed = true)
        val chapter = mockk<Chapter>(relaxed = true)
        every { chapter.id } returns 1L
        return MangaDownload(
            source = source,
            manga = manga,
            chapter = chapter,
        )
    }
}
