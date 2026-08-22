package eu.kanade.tachiyomi.ui.reader.viewer.pager

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PagerViewerCurlGatingTest {

    @Test
    fun `does not attempt curl when style is NONE`() {
        PagerViewer.shouldAttemptCurl(
            style = PageTransitionStyle.NONE,
            targetIsChapterTransition = false,
            sourceAtMinimumZoom = true,
            targetAtMinimumZoom = true,
            withinRapidNavigationWindow = false,
        ) shouldBe false
    }

    @Test
    fun `does not attempt curl when style is SLIDE`() {
        PagerViewer.shouldAttemptCurl(
            style = PageTransitionStyle.SLIDE,
            targetIsChapterTransition = false,
            sourceAtMinimumZoom = true,
            targetAtMinimumZoom = true,
            withinRapidNavigationWindow = false,
        ) shouldBe false
    }

    @Test
    fun `does not attempt curl for a chapter transition target`() {
        PagerViewer.shouldAttemptCurl(
            style = PageTransitionStyle.CURL,
            targetIsChapterTransition = true,
            sourceAtMinimumZoom = true,
            targetAtMinimumZoom = true,
            withinRapidNavigationWindow = false,
        ) shouldBe false
    }

    @Test
    fun `does not attempt curl when source page is zoomed`() {
        PagerViewer.shouldAttemptCurl(
            style = PageTransitionStyle.CURL,
            targetIsChapterTransition = false,
            sourceAtMinimumZoom = false,
            targetAtMinimumZoom = true,
            withinRapidNavigationWindow = false,
        ) shouldBe false
    }

    @Test
    fun `does not attempt curl when target page is zoomed`() {
        PagerViewer.shouldAttemptCurl(
            style = PageTransitionStyle.CURL,
            targetIsChapterTransition = false,
            sourceAtMinimumZoom = true,
            targetAtMinimumZoom = false,
            withinRapidNavigationWindow = false,
        ) shouldBe false
    }

    @Test
    fun `does not attempt curl during rapid navigation`() {
        PagerViewer.shouldAttemptCurl(
            style = PageTransitionStyle.CURL,
            targetIsChapterTransition = false,
            sourceAtMinimumZoom = true,
            targetAtMinimumZoom = true,
            withinRapidNavigationWindow = true,
        ) shouldBe false
    }

    @Test
    fun `attempts curl when style is CURL and all preconditions hold`() {
        PagerViewer.shouldAttemptCurl(
            style = PageTransitionStyle.CURL,
            targetIsChapterTransition = false,
            sourceAtMinimumZoom = true,
            targetAtMinimumZoom = true,
            withinRapidNavigationWindow = false,
        ) shouldBe true
    }
}
