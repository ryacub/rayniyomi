package eu.kanade.presentation.more.settings.widget

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun themeSection_customThemeFlow_endToEnd() {
        composeRule.setContent {
            var appTheme by mutableStateOf(AppTheme.DEFAULT)
            var selectedAccentSeed by mutableIntStateOf(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET)
            var showPicker by mutableStateOf(false)
            var pickerSession by mutableIntStateOf(0)
            var pickerSeed by mutableIntStateOf(resolveInitialCustomAccentPickerSeed(selectedAccentSeed))

            MaterialTheme {
                Column {
                    AppThemePreferenceWidget(
                        value = appTheme,
                        amoled = false,
                        onItemClick = { appTheme = it },
                    )

                    if (appTheme == AppTheme.CUSTOM) {
                        CustomThemeAccentPreferenceWidget(
                            selectedAccentSeed = selectedAccentSeed,
                            onSwatchClick = { selectedAccentSeed = normalizeAccentSeed(it) },
                            onOpenPicker = {
                                pickerSeed = resolveInitialCustomAccentPickerSeed(selectedAccentSeed)
                                pickerSession = nextCustomAccentPickerSession(pickerSession)
                                showPicker = true
                            },
                            onReset = { selectedAccentSeed = UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET },
                        )
                    }

                    if (showPicker) {
                        CustomThemeColorPickerDialog(
                            sessionKey = pickerSession,
                            initialSeed = pickerSeed,
                            onDismiss = { showPicker = false },
                            onApply = { selectedAccentSeed = normalizeAccentSeed(it) },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Custom accent").assertDoesNotExist()

        composeRule.onNodeWithText("Custom").performClick()
        composeRule.onNodeWithText("Custom accent").assertExists()

        composeRule.onNodeWithContentDescription("Accent swatch #E53935").performClick()
        composeRule.onNodeWithText("Selected accent: #E53935").assertExists()

        composeRule.onNodeWithText("Custom color…").performClick()
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
        composeRule.onNodeWithText("Selected accent: #FFFFFF").assertExists()

        composeRule.onNodeWithText("Reset accent").performClick()
        composeRule.onNodeWithText("Using default accent fallback").assertExists()

        composeRule.onNodeWithText("Custom color…").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Using default accent fallback").assertExists()
    }
}
