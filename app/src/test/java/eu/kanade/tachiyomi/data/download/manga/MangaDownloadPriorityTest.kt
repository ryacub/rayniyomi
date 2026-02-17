package eu.kanade.tachiyomi.data.download.manga

import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.download.model.DownloadPriority
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

class MangaDownloadPriorityTest {

    private fun createMockDownload(
        chapterId: Long,
        priority: DownloadPriority = DownloadPriority.NORMAL,
    ): MangaDownload {
        return MangaDownload(
            source = mockk(relaxed = true),
            manga = Manga.create().copy(id = 1L, title = "Test Manga"),
            chapter = Chapter.create().copy(id = chapterId, name = "Chapter $chapterId"),
            priority = priority,
        )
    }

    @Test
    fun `downloads are ordered by priority then queue position`() {
        val downloads = listOf(
            createMockDownload(1, DownloadPriority.NORMAL),
            createMockDownload(2, DownloadPriority.LOW),
            createMockDownload(3, DownloadPriority.HIGH),
            createMockDownload(4, DownloadPriority.NORMAL),
            createMockDownload(5, DownloadPriority.HIGH),
        )

        val sorted = downloads.sortedWith(
            compareByDescending<MangaDownload> { it.priority.value }
                .thenBy { downloads.indexOf(it) },
        )

        // Expected order: HIGH (3, 5), NORMAL (1, 4), LOW (2)
        assertEquals(3L, sorted[0].chapter.id)
        assertEquals(5L, sorted[1].chapter.id)
        assertEquals(1L, sorted[2].chapter.id)
        assertEquals(4L, sorted[3].chapter.id)
        assertEquals(2L, sorted[4].chapter.id)
    }

    @Test
    fun `default priority is NORMAL`() {
        val download = createMockDownload(1)
        assertEquals(DownloadPriority.NORMAL, download.priority)
    }

    @Test
    fun `priority can be changed after creation`() {
        val download = createMockDownload(1, DownloadPriority.NORMAL)
        download.priority = DownloadPriority.HIGH
        assertEquals(DownloadPriority.HIGH, download.priority)
    }
}
