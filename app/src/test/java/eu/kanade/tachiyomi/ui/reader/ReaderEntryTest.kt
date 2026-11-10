package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.util.chapter.StartAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.Chapter

class ReaderEntryTest {

    private val chapter = Chapter.create().copy(id = 1L, mangaId = 1L)

    @Test
    fun `first start uses the reader slide at normal animation speed`() {
        assertEquals(ReaderEntry.Default, readerEntry(StartAction.FirstStart(chapter), animatorScale = 1f))
    }

    @Test
    fun `first start skips the peel when animations are off`() {
        assertEquals(ReaderEntry.Immediate, readerEntry(StartAction.FirstStart(chapter), animatorScale = 0f))
    }

    @Test
    fun `no start action keeps the default reader transition`() {
        assertEquals(ReaderEntry.Default, readerEntry(StartAction.None, animatorScale = 1f))
    }

    @Test
    fun `resume fades in at normal animation speed`() {
        assertEquals(ReaderEntry.Fade, readerEntry(StartAction.Resume(chapter), animatorScale = 1f))
    }

    @Test
    fun `resume skips the fade when animations are off`() {
        assertEquals(ReaderEntry.Immediate, readerEntry(StartAction.Resume(chapter), animatorScale = 0f))
    }
}
