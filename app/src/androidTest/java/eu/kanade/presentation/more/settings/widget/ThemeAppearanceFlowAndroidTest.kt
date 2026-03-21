package eu.kanade.presentation.more.settings.widget

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.more.settings.screen.nextCustomAccentPickerSession
import eu.kanade.presentation.more.settings.screen.resolveInitialCustomAccentPickerSeed
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeAppearanceFlowAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeSection_customThemeFlow_endToEnd() {
        composeRule.setContent {
            var appTheme by mutableStateOf(AppTheme.DEFAULT)
            var selectedAccentSeed by mutableIntStateOf(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET)
            var showPicker by mutableStateOf(false)
            var pickerSession by mutableIntStateOf(0)
            var pickerSeed by mutableIntStateOf(resolveInitialCustomAccentPickerSeed(selectedAccentSeed))
            var announcement by mutableStateOf<String?>(null)

            MaterialTheme {
                TextButton(
                    onClick = { appTheme = AppTheme.CUSTOM },
                    modifier = Modifier.testTag("theme_set_custom"),
                ) {
                    Text("Set custom")
                }

                if (appTheme == AppTheme.CUSTOM) {
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
                        onSwatchAnnouncement = { announcement = it },
                    )
                }

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

        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_SWATCH_ROW, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("theme_set_custom").performClick()

        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_SWATCH_ROW, useUnmergedTree = true).assertExists()
        // Assert the primary control sequence expected by the custom-accent accessibility contract.
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_BUTTON_PICK).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_BUTTON_RESET).assertExists()

        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_BUTTON_PICK).performClick()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_PICKER_PREVIEW).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_SLIDER_HUE).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_SLIDER_SATURATION).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_SLIDER_VALUE).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_PICKER_APPLY).assertExists()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_PICKER_CANCEL).assertExists()
        composeRule.onNodeWithContentDescription("Hue").performSemanticsAction(SemanticsActions.SetProgress) {
            it(240f)
        }
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_PICKER_CANCEL).performClick()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_PICKER_PREVIEW).assertDoesNotExist()

        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_BUTTON_PICK).performClick()
        composeRule.onNodeWithContentDescription("Saturation").performSemanticsAction(SemanticsActions.SetProgress) {
            it(0f)
        }
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_PICKER_APPLY).performClick()
        composeRule.onNodeWithTag(TAG_CUSTOM_ACCENT_PICKER_PREVIEW).assertDoesNotExist()
    }
}
