package eu.kanade.presentation.entries.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EntryActionRowLibraryFeedbackAndroidTest {

    @get:Rule
    val compose = createComposeRule()

    private val haptic = CountingHaptic()
    private val favorite = mutableStateOf(false)
    private val confirmations = mutableIntStateOf(0)

    @Test
    fun addingATitleBouncesTheIconOnce() {
        show(initial = false)

        compose.runOnIdle { confirmAdd() }
        compose.mainClock.advanceTimeBy(150)
        val peak = iconWidth()
        compose.mainClock.advanceTimeBy(500)
        val rest = iconWidth()

        assertTrue("icon width at the bounce peak was $peak", peak > 23.dp)
        assertTrue("icon width after the bounce was $rest", rest < 20.5.dp)
    }

    @Test
    fun theFirstDrawOfALibraryTitleDoesNotBounceOrBuzz() {
        show(initial = true)

        compose.mainClock.advanceTimeBy(150)
        val width = iconWidth()

        assertTrue("icon width on first draw was $width", width < 20.5.dp)
        assertEquals(0, haptic.count)
    }

    @Test
    fun aLibraryChangeFiresOneHaptic() {
        show(initial = false)

        compose.runOnIdle { confirmAdd() }
        compose.mainClock.advanceTimeBy(500)

        assertEquals(1, haptic.count)
    }

    @Test
    fun aLibraryChangeFromElsewhereDoesNotBounceOrBuzz() {
        show(initial = true)

        compose.runOnIdle { favorite.value = false }
        compose.mainClock.advanceTimeBy(150)
        val width = iconWidth()

        assertTrue("icon width after an unconfirmed change was $width", width < 20.5.dp)
        assertEquals(0, haptic.count)
    }

    @Test
    fun theLibraryButtonAnnouncesLabelChanges() {
        show(initial = true)

        compose.onNodeWithText("In library")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    private fun confirmAdd() {
        favorite.value = true
        confirmations.intValue++
    }

    private fun show(initial: Boolean) {
        favorite.value = initial
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                    EntryActionRow(
                        favorite = favorite.value,
                        libraryConfirmations = confirmations.value,
                        trackingCount = 0,
                        nextUpdate = null,
                        isUserIntervalMode = false,
                        onAddToLibraryClicked = {},
                        onWebViewClicked = null,
                        onWebViewLongClicked = null,
                        onTrackingClicked = null,
                        onEditIntervalClicked = null,
                        onEditCategory = null,
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(32)
    }

    private fun iconWidth(): Dp {
        val node = compose.onNodeWithTag("entry-library-icon", useUnmergedTree = true).fetchSemanticsNode()
        return with(node.layoutInfo.density) { node.boundsInRoot.width.toDp() }
    }

    private class CountingHaptic : HapticFeedback {
        var count = 0

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            count++
        }
    }
}
