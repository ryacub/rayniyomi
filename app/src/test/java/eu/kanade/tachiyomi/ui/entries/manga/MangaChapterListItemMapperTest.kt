package eu.kanade.tachiyomi.ui.entries.manga

import eu.kanade.tachiyomi.data.download.core.DownloadQueueMutations
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.translation.TranslationState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.source.local.entries.manga.LocalMangaSource

class MangaChapterListItemMapperTest {

    private val downloadManager = mockk<MangaDownloadManager>()
    private val mapper = MangaChapterListItemMapper(downloadManager)

    @Test
    fun `queued download takes precedence over downloaded file`() {
        val chapter = chapter(1L)
        val queuedDownload = mockk<MangaDownload> {
            every { status } returns MangaDownload.State.DOWNLOADING
            every { progress } returns 42
            every { progressFlow } returns MutableStateFlow(42)
        }
        every { queuedDownload.chapter } returns chapter
        every { downloadManager.queueState } returns MutableStateFlow(listOf(queuedDownload))
        every { downloadManager.isChapterDownloaded(any(), any(), any(), any()) } returns true

        val item = mapper.map(
            chapters = listOf(chapter),
            manga = manga(),
            selectedChapterIds = emptySet(),
            translationStates = emptyMap(),
        ).single()

        assertEquals(MangaDownload.State.DOWNLOADING, item.downloadState)
        assertEquals(42, item.downloadProgress)
    }

    @Test
    fun `maps downloaded and missing files while preserving input order`() {
        val chapters = listOf(chapter(2L), chapter(1L))
        every { downloadManager.queueState } returns MutableStateFlow(emptyList())
        every { downloadManager.isChapterDownloaded(any(), any(), any(), any()) } answers {
            firstArg<String>() == "Chapter 2"
        }

        val items = mapper.map(
            chapters = chapters,
            manga = manga(),
            selectedChapterIds = emptySet(),
            translationStates = emptyMap(),
        )

        assertEquals(listOf(2L, 1L), items.map { it.id })
        assertEquals(MangaDownload.State.DOWNLOADED, items[0].downloadState)
        assertEquals(MangaDownload.State.NOT_DOWNLOADED, items[1].downloadState)
    }

    @Test
    fun `local chapters are downloaded without querying download manager`() {
        val chapters = listOf(chapter(1L), chapter(2L))
        val translatedState = TranslationState.Translated
        every { downloadManager.getQueuedDownloadOrNull(any()) } throws AssertionError("local chapter queried queue")
        every {
            downloadManager.isChapterDownloaded(any(), any(), any(), any())
        } throws AssertionError("local chapter queried files")

        val items = mapper.map(
            chapters = chapters,
            manga = manga(source = LocalMangaSource.ID),
            selectedChapterIds = setOf(2L),
            translationStates = mapOf(2L to translatedState),
        )

        assertEquals(
            listOf(MangaDownload.State.DOWNLOADED, MangaDownload.State.DOWNLOADED),
            items.map {
                it.downloadState
            },
        )
        assertEquals(listOf(false, true), items.map { it.selected })
        assertEquals(listOf(TranslationState.Idle, translatedState), items.map { it.translationState })
        verify(exactly = 0) { downloadManager.getQueuedDownloadOrNull(any()) }
        verify(exactly = 0) { downloadManager.isChapterDownloaded(any(), any(), any(), any()) }
    }

    @Test
    fun `mapping inspects the queue once instead of once per chapter`() = runTest {
        val downloads = (1L..4L).map { id ->
            MangaDownload(source = mockk(), manga = manga(), chapter = chapter(id)).apply {
                status = MangaDownload.State.DOWNLOADING
            }
        } + MangaDownload(source = mockk(), manga = manga(), chapter = chapter(1L)).apply {
            status = MangaDownload.State.ERROR
        }
        var queueReads = 0
        val queue = object : AbstractList<MangaDownload>() {
            override val size: Int get() = downloads.size
            override fun get(index: Int): MangaDownload {
                queueReads++
                return downloads[index]
            }
        }
        val queueState = MutableStateFlow<List<MangaDownload>>(queue)
        val mutations = DownloadQueueMutations<MangaDownload, Chapter>(
            queueState = queueState,
            itemId = { it.chapter.id },
            ownerItemId = { it.id },
            createFromId = { null },
            moveToFront = {},
            updateQueue = {},
            addToStart = {},
            removeFromQueueByIds = {},
            isRunning = { false },
            pauseDownloader = {},
            startDownloader = {},
            stopDownloader = {},
            startDownloads = {},
            mutationScope = backgroundScope,
        )
        every { downloadManager.queueState } returns queueState
        every { downloadManager.getQueuedDownloadOrNull(any()) } answers {
            mutations.getQueuedDownloadOrNull(firstArg())
        }
        every { downloadManager.isChapterDownloaded(any(), any(), any(), any()) } returns false
        val chapters = listOf(chapter(1L)) + (101L..108L).map(::chapter)

        val items = mapper.map(chapters, manga(), setOf(102L), mapOf(103L to TranslationState.Translated))

        assertEquals(chapters.map { it.id }, items.map { it.id })
        assertEquals(MangaDownload.State.DOWNLOADING, items[0].downloadState)
        assertEquals(true, items.single { it.id == 102L }.selected)
        assertEquals(TranslationState.Translated, items.single { it.id == 103L }.translationState)
        assertTrue(queueReads <= downloads.size, "Expected at most ${downloads.size} queue reads, got $queueReads")
    }

    private fun manga(source: Long = 1L) = Manga.create().copy(id = 1L, source = source, title = "Manga")

    private fun chapter(id: Long) = Chapter.create().copy(id = id, mangaId = 1L, name = "Chapter $id")
}
