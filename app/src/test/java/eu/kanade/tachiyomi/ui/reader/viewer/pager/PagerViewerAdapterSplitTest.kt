package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PagerViewerAdapterSplitTest {

    @AfterEach
    fun tearDown() = unmockkAll()

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `a detached current chapter page cannot insert a split`(rightToLeft: Boolean) {
        val viewer = if (rightToLeft) mockk<R2LPagerViewer>(relaxed = true) else mockk<L2RPagerViewer>(relaxed = true)
        mockkStatic("eu.kanade.tachiyomi.util.system.ContextExtensionsKt")
        every { any<Context>().createReaderThemeContext() } returns mockk(relaxed = true)
        val adapter = PagerViewerAdapter(viewer)
        val chapter = ReaderChapter(ChapterImpl(id = 1L))
        adapter.currentChapter = chapter
        val visiblePage = ReaderPage(0).apply { this.chapter = chapter }
        val detachedPage = ReaderPage(1).apply { this.chapter = chapter }
        adapter.items.add(visiblePage)

        adapter.onPageSplit(detachedPage, InsertPage(detachedPage))

        assertEquals(listOf(visiblePage), adapter.items)
        adapter.items.clear()
        adapter.onPageSplit(detachedPage, InsertPage(detachedPage))
        assertTrue(adapter.items.isEmpty())
        adapter.items.add(visiblePage)
        val split = InsertPage(visiblePage)
        adapter.onPageSplit(visiblePage, split)
        assertEquals(if (rightToLeft) listOf(split, visiblePage) else listOf(visiblePage, split), adapter.items)
        adapter.onPageSplit(visiblePage, InsertPage(visiblePage))
        assertEquals(2, adapter.items.size)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `late previous chapter splits cannot corrupt the current chapter`(rightToLeft: Boolean) {
        for (pageCount in listOf(2, 50)) {
            val adapter = createAdapter(rightToLeft)
            val previous = createChapter(1L, 50)
            val current = createChapter(2L, pageCount)
            val chapters = ViewerChapters(current, previous, null)
            adapter.setChapters(ViewerChapters(previous, null, null), false)
            adapter.setChapters(chapters, false)

            adapter.onPageSplit(previous.pages!!.last(), InsertPage(previous.pages!!.last()))

            assertDoesNotThrow { adapter.setChapters(chapters, false) }
            assertTrue(adapter.items.filterIsInstance<InsertPage>().isEmpty())
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `a preprocessed split cannot reuse a parent replaced by a chapter reload`(rightToLeft: Boolean) {
        val adapter = createAdapter(rightToLeft)
        val current = createChapter(1L, 2)
        val next = createChapter(2L, 2)
        adapter.setChapters(ViewerChapters(current, null, next), false)
        val oldParent = next.pages!!.last()
        adapter.onPageSplit(oldParent, InsertPage(oldParent))
        val freshPages = List(2) { ReaderPage(it).apply { chapter = next } }
        next.state = ReaderChapter.State.Loaded(freshPages)

        adapter.setChapters(ViewerChapters(next, current, null), false)

        assertTrue(adapter.items.filterIsInstance<InsertPage>().isEmpty())
        assertEquals(
            if (rightToLeft) freshPages.reversed() else freshPages,
            adapter.items.filterIsInstance<ReaderPage>().filter {
                it.chapter ===
                    next
            },
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `next chapter splits survive late previous chapter callbacks`(rightToLeft: Boolean) {
        val viewer = createViewer(rightToLeft)
        val adapter = PagerViewerAdapter(viewer)
        val previous = createChapter(1L, 50)
        val current = createChapter(2L, 2)
        val next = createChapter(3L, 2)
        adapter.setChapters(ViewerChapters(current, previous, next), false)
        val parents = next.pages!!
        val splits = parents.map(::InsertPage)
        parents.forEachIndexed { index, parent -> adapter.onPageSplit(parent, splits[index]) }
        adapter.onPageSplit(previous.pages!!.last(), InsertPage(previous.pages!!.last()))
        adapter.onPageSplit(previous.pages!![1], InsertPage(previous.pages!![1]))

        adapter.setChapters(ViewerChapters(next, current, null), false)

        val expected = parents.flatMapIndexed { index, parent -> listOf(parent, splits[index]) }
        val actual = adapter.items.filterIsInstance<ReaderPage>().filter { it.chapter === next }
        assertEquals(if (rightToLeft) expected.reversed() else expected, actual)
        verify(exactly = 1) { viewer.moveToPage(splits.last()) }
    }

    private fun createAdapter(rightToLeft: Boolean) = PagerViewerAdapter(createViewer(rightToLeft))

    private fun createViewer(rightToLeft: Boolean): PagerViewer {
        val viewer = if (rightToLeft) mockk<R2LPagerViewer>(relaxed = true) else mockk<L2RPagerViewer>(relaxed = true)
        mockkStatic("eu.kanade.tachiyomi.util.system.ContextExtensionsKt")
        every { any<Context>().createReaderThemeContext() } returns mockk(relaxed = true)
        return viewer
    }

    private fun createChapter(id: Long, pageCount: Int): ReaderChapter {
        val chapter = ReaderChapter(
            ChapterImpl(id).apply {
                manga_id = 1L
                url = "chapter-$id"
                name = "Chapter $id"
                chapter_number = id.toFloat()
            },
        )
        chapter.state = ReaderChapter.State.Loaded(List(pageCount) { ReaderPage(it).apply { this.chapter = chapter } })
        return chapter
    }
}
