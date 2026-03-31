package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderErrorUiContractTest {

    @Test
    fun `canOpenReaderPageInWebView returns true for http and https urls`() {
        assertTrue(canOpenReaderPageInWebView("http://example.com/page.jpg"))
        assertTrue(canOpenReaderPageInWebView("https://example.com/page.jpg"))
    }

    @Test
    fun `canOpenReaderPageInWebView returns false for null and non-http urls`() {
        assertFalse(canOpenReaderPageInWebView(null))
        assertFalse(canOpenReaderPageInWebView("content://example/image"))
        assertFalse(canOpenReaderPageInWebView("file:///tmp/image.jpg"))
    }

    @Test
    fun `ReaderErrorUiActions are callback-only and executable`() {
        var retried = false
        var opened = false
        var pressed: Boolean? = null

        val actions = ReaderErrorUiActions(
            onRetry = { retried = true },
            onOpenInWebView = { opened = true },
            onActionPressChanged = { pressed = it },
        )

        actions.onRetry()
        actions.onOpenInWebView()
        actions.onActionPressChanged(true)
        actions.onActionPressChanged(false)

        assertTrue(retried)
        assertTrue(opened)
        assertFalse(pressed ?: true)
    }
}
