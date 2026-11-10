package eu.kanade.presentation.entries.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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

    @Test
    fun addingATitleBouncesTheIconOnce() {
        val favorite = show(initial = false)

        compose.runOnIdle { favorite.value = true }
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
        val favorite = show(initial = false)

        compose.runOnIdle { favorite.value = true }
        compose.mainClock.advanceTimeBy(500)

        assertEquals(1, haptic.count)
    }

    @Test
    fun theLibraryButtonAnnouncesLabelChanges() {
        show(initial = true)

        compose.onNodeWithText("In library")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    private fun show(initial: Boolean): MutableState<Boolean> {
        val favorite = mutableStateOf(initial)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalHapticFeedback provides haptic) {
                    EntryActionRow(
                        favorite = favorite.value,
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
        return favorite
    }

    private fun iconWidth(): Dp {
        val bounds = compose.onNodeWithTag("entry-library-icon", useUnmergedTree = true).getUnclippedBoundsInRoot()
        return bounds.right - bounds.left
    }

    private class CountingHaptic : HapticFeedback {
        var count = 0

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            count++
        }
    }
}
