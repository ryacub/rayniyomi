package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SelectedPageReadyTest {

    @Test
    fun `completes when the selected page becomes ready`() = runTest {
        val page = Page(index = 4)
        val selected = MutableStateFlow<Page?>(null)
        val ready = async { selected.awaitSelectedPageReady() }

        selected.value = page
        runCurrent()
        assertFalse(ready.isCompleted)

        page.status = Page.State.READY
        runCurrent()
        assertTrue(ready.isCompleted)
    }

    @Test
    fun `ignores a page that is no longer selected`() = runTest {
        val first = Page(index = 0)
        val target = Page(index = 9)
        val selected = MutableStateFlow<Page?>(first)
        val ready = async { selected.awaitSelectedPageReady() }
        runCurrent()

        selected.value = target
        runCurrent()
        first.status = Page.State.READY
        runCurrent()
        assertFalse(ready.isCompleted)

        target.status = Page.State.READY
        runCurrent()
        assertTrue(ready.isCompleted)
    }

    @Test
    fun `page error does not count as ready`() = runTest {
        val page = Page(index = 2)
        val selected = MutableStateFlow<Page?>(page)
        val ready = async { selected.awaitSelectedPageReady() }

        page.status = Page.State.ERROR
        runCurrent()

        assertFalse(ready.isCompleted)
        ready.cancel()
    }
}
