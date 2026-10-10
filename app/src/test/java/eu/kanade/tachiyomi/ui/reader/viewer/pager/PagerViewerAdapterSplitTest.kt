package eu.kanade.tachiyomi.ui.reader.viewer.pager

import android.content.Context
import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
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
}
