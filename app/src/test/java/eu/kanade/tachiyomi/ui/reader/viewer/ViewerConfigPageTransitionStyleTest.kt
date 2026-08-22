package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import eu.kanade.tachiyomi.ui.updates.InMemoryPreferenceStore
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerConfigPageTransitionStyleTest {

    private class TestViewerConfig(
        readerPreferences: ReaderPreferences,
        scope: CoroutineScope,
    ) : ViewerConfig(readerPreferences, scope) {
        override var navigator: ViewerNavigation = mockk(relaxed = true)
        override fun defaultNavigation(): ViewerNavigation = navigator
        override fun updateNavigation(navigationMode: Int) = Unit
    }

    private val store = InMemoryPreferenceStore()
    private val readerPreferences = ReaderPreferences(store)
    private val scope = TestScope(UnconfinedTestDispatcher())

    @Test
    fun `pageTransitionStyle starts at the preference value`() {
        val config = TestViewerConfig(readerPreferences, scope)

        assertEquals(PageTransitionStyle.SLIDE, config.pageTransitionStyle)
    }

    @Test
    fun `pageTransitionStyle follows a preference change`() {
        val config = TestViewerConfig(readerPreferences, scope)

        readerPreferences.pageTransitionStyle().set(PageTransitionStyle.CURL)

        assertEquals(PageTransitionStyle.CURL, config.pageTransitionStyle)
    }

    @Test
    fun `pageTransitionStyle follows a change to NONE`() {
        val config = TestViewerConfig(readerPreferences, scope)

        readerPreferences.pageTransitionStyle().set(PageTransitionStyle.NONE)

        assertEquals(PageTransitionStyle.NONE, config.pageTransitionStyle)
    }
}
