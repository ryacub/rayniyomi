package eu.kanade.presentation.util

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.navigator.Navigator
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.MangaCover

@RunWith(AndroidJUnit4::class)
class LibraryCoverTransitionAndroidTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lateAnimeCoverAndMovedLibraryTileAreMatched() {
        val fixture = Fixture(isManga = false)
        compose.setContent { fixture.Content() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.runOnIdle { fixture.open() }
        advance(64)
        compose.runOnIdle { fixture.showDetail.value = true }
        advance(416)
        val forwardBounds = overlay.getUnclippedBoundsInRoot()
        assertTrue(forwardBounds.left > 12.dp && forwardBounds.left < 100.dp)
        assertTrue(
            (forwardBounds.right - forwardBounds.left) > 32.dp && (forwardBounds.right - forwardBounds.left) < 80.dp,
        )
        advance(1_216)
        overlay.assertDoesNotExist()

        compose.runOnIdle {
            fixture.libraryOffset.value = 180.dp
            fixture.navigator.pop()
        }
        advance(416)
        val returnBounds = overlay.getUnclippedBoundsInRoot()
        assertTrue(returnBounds.left > 100.dp && returnBounds.left < 180.dp)
        assertTrue((returnBounds.right - returnBounds.left) < 80.dp && (returnBounds.right - returnBounds.left) > 32.dp)
        advance(1_216)
        overlay.assertDoesNotExist()
    }

    @Test
    fun backBeforeDetailLoadsClearsTheOverlay() {
        val fixture = Fixture()
        compose.setContent { fixture.Content() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.runOnIdle { fixture.open() }
        advance(48)
        overlay.assertExists()
        compose.runOnIdle { fixture.navigator.pop() }
        advance(1_216)
        overlay.assertDoesNotExist()
        assertTrue(fixture.navigator.items.size == 1)
    }

    @Test
    fun backToAnOffscreenTileUsesTheScreenTransition() {
        val fixture = Fixture()
        compose.setContent { fixture.Content() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.runOnIdle {
            fixture.showDetail.value = true
            fixture.open()
        }
        advance(1_216)
        compose.runOnIdle {
            fixture.libraryOffset.value = 1_000.dp
            fixture.navigator.pop()
        }
        advance(416)
        overlay.assertDoesNotExist()
        advance(1_216)
        assertTrue(fixture.navigator.items.size == 1)
    }

    @Test
    fun backFromDetailsStillMatchesAfterVisitingAnotherScreen() {
        val fixture = Fixture()
        compose.setContent { fixture.Content() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.runOnIdle {
            fixture.showDetail.value = true
            fixture.open()
        }
        advance(1_216)
        compose.runOnIdle { fixture.navigator.push(ChildScreen) }
        advance(1_216)
        compose.runOnIdle { fixture.navigator.pop() }
        advance(1_216)
        compose.runOnIdle { fixture.navigator.pop() }
        advance(416)
        overlay.assertExists()
    }

    @Test
    fun backDuringForwardMotionReversesFromTheDisplayedCover() {
        val fixture = Fixture()
        compose.setContent { fixture.Content() }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false

        compose.runOnIdle {
            fixture.showDetail.value = true
            fixture.open()
        }
        advance(416)
        val displayedLeft = overlay.getUnclippedBoundsInRoot().left
        assertTrue(displayedLeft > 12.dp && displayedLeft < 100.dp)

        compose.runOnIdle { fixture.navigator.pop() }
        advance(416)
        val reversingLeft = overlay.getUnclippedBoundsInRoot().left
        assertTrue(
            "Cover moved from $displayedLeft to $reversingLeft",
            reversingLeft < displayedLeft && reversingLeft > 12.dp,
        )
        advance(1_216)
        overlay.assertDoesNotExist()
    }

    private object ChildScreen : Screen() {
        @Composable
        override fun Content() {
            Box(Modifier.fillMaxSize())
        }
    }

    private val overlay get() = compose.onNodeWithTag("library-cover-motion")

    private fun advance(milliseconds: Long) {
        repeat((milliseconds / 16).toInt()) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    private class Fixture(private val isManga: Boolean = true) {
        private val model = Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888)
        private val cover = if (isManga) MangaCover(1L, 0L, true, null, 0L) else AnimeCover(1L, 0L, true, null, 0L)
        val showDetail = mutableStateOf(false)
        val libraryOffset = mutableStateOf(12.dp)
        lateinit var navigator: Navigator
        private lateinit var transition: LibraryCoverTransition

        private val detail = object : Screen() {
            @Composable
            override fun Content() {
                Box(Modifier.fillMaxSize()) {
                    if (showDetail.value) {
                        LibraryTransitionCover(
                            data = cover,
                            model = model,
                            modifier = Modifier.offset(x = 100.dp, y = 100.dp).width(80.dp),
                            source = false,
                        )
                    }
                }
            }
        }

        private val library = object : Screen() {
            @Composable
            override fun Content() {
                transition = checkNotNull(LocalLibraryCoverTransition.current)
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalActiveCoverPage provides true) {
                        LibraryTransitionCover(
                            data = cover,
                            model = model,
                            modifier = Modifier.offset(x = libraryOffset.value, y = 24.dp).width(32.dp),
                            source = true,
                        )
                    }
                }
            }
        }

        fun open() = transition.open(navigator, detail, LibraryCoverKey(isManga, id = 1L))

        @Composable
        fun Content() {
            MaterialTheme {
                Navigator(library) { current ->
                    navigator = current
                    LibraryCoverTransitionHost(current, Modifier.fillMaxSize())
                }
            }
        }
    }
}
