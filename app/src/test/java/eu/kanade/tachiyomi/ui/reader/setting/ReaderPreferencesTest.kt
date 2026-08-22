package eu.kanade.tachiyomi.ui.reader.setting

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import eu.kanade.tachiyomi.ui.updates.InMemoryPreferenceStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReaderPreferencesTest {

    private val store = InMemoryPreferenceStore()
    private val readerPreferences = ReaderPreferences(store)

    @Test
    fun `pageTransitionStyle defaults to SLIDE on a fresh store`() {
        assertEquals(PageTransitionStyle.SLIDE, readerPreferences.pageTransitionStyle().get())
    }

    @Test
    fun `pageTransitionStyle round-trips CURL`() {
        readerPreferences.pageTransitionStyle().set(PageTransitionStyle.CURL)

        assertEquals(PageTransitionStyle.CURL, readerPreferences.pageTransitionStyle().get())
    }

    @Test
    fun `pageTransitionStyle round-trips NONE`() {
        readerPreferences.pageTransitionStyle().set(PageTransitionStyle.NONE)

        assertEquals(PageTransitionStyle.NONE, readerPreferences.pageTransitionStyle().get())
    }
}
