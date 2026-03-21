package eu.kanade.presentation.more.settings.widget

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.more.settings.screen.nextCustomAccentPickerSession
import eu.kanade.presentation.more.settings.screen.resolveInitialCustomAccentPickerSeed
import eu.kanade.presentation.theme.colorscheme.CustomAccentContrastMode
import eu.kanade.presentation.theme.colorscheme.CustomAccentContrastWarning
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CustomThemeAccentPreferenceWidgetAndroidTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun customAccentFlow_swatch_pickerCancel_pickerApply_reset_reopen() {
        composeRule.setContent {
            var selectedAccentSeed by mutableIntStateOf(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET)
            var showPicker by mutableStateOf(false)
            var pickerSession by mutableIntStateOf(0)
            var pickerSeed by mutableIntStateOf(
                resolveInitialCustomAccentPickerSeed(selectedAccentSeed),
            )
            var announcement by mutableStateOf<String?>(null)

            MaterialTheme {
                CustomThemeAccentPreferenceWidget(
                    selectedAccentSeed = selectedAccentSeed,
                    recentAccentSeeds = emptyList(),
                    onSwatchClick = { selectedAccentSeed = normalizeAccentSeed(it) },
                    onRecentColorClick = { selectedAccentSeed = normalizeAccentSeed(it) },
                    onOpenPicker = {
                        pickerSeed = resolveInitialCustomAccentPickerSeed(selectedAccentSeed)
                        pickerSession = nextCustomAccentPickerSession(pickerSession)
                        showPicker = true
                    },
                    onOpenAdvancedEditor = {},
                    onReset = { selectedAccentSeed = UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET },
                    accessibilityAnnouncement = announcement,
                    contrastWarningOverride = if (normalizeAccentSeed(selectedAccentSeed) == 0xFFE53935.toInt()) {
                        CustomAccentContrastWarning(setOf(CustomAccentContrastMode.LIGHT))
                    } else {
                        CustomAccentContrastWarning(emptySet())
                    },
                    onSwatchAnnouncement = { announcement = it },
                )
                if (showPicker) {
                    CustomThemeColorPickerDialog(
                        sessionKey = pickerSession,
                        initialSeed = pickerSeed,
                        onDismiss = { showPicker = false },
                        onApply = { selectedAccentSeed = normalizeAccentSeed(it) },
                        onAppliedAnnouncement = { announcement = it },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_SWATCH_ROW).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_BUTTON_PICK).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_BUTTON_RESET).assertExists()

        composeRule.onNodeWithContentDescription("Accent swatch #E53935").performClick()
        composeRule.onNodeWithText("Selected accent: #E53935").assertExists()
        composeRule.onNodeWithText("Warning: low contrast in Light").assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_ANNOUNCEMENT).assertExists()

        composeRule.onNodeWithText("Custom color…").performClick()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_PICKER_PREVIEW).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_SLIDER_HUE).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_SLIDER_SATURATION).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_SLIDER_VALUE).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_PICKER_APPLY).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_PICKER_CANCEL).assertExists()
        composeRule.onNodeWithContentDescription("Hue").performSemanticsAction(SemanticsActions.SetProgress) {
            it(240f)
        }
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Selected accent: #E53935").assertExists()

        composeRule.onNodeWithText("Custom color…").performClick()
        composeRule.onNodeWithContentDescription("Saturation").performSemanticsAction(SemanticsActions.SetProgress) {
            it(0f)
        }
        composeRule.onNodeWithText("Apply").performClick()
        composeRule.onNodeWithText("Selected accent: #E53935").assertDoesNotExist()
        composeRule.onNodeWithText("Selected accent: #FFFFFF").assertExists()
        composeRule.onNodeWithText("Warning: low contrast in Light").assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_ANNOUNCEMENT).assertExists()

        composeRule.onNodeWithText("Reset accent").performClick()
        composeRule.onNodeWithText("Using default accent fallback").assertExists()

        composeRule.onNodeWithText("Custom color…").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Using default accent fallback").assertExists()
    }
}
