package eu.kanade.presentation.more.settings.screen.appearance

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.more.settings.widget.CustomThemeAccentEditor
import eu.kanade.presentation.more.settings.widget.accentSeedToHex
import eu.kanade.presentation.more.settings.widget.hsvToAccentSeed
import eu.kanade.presentation.more.settings.widget.normalizeAccentSeed
import eu.kanade.presentation.more.settings.widget.seedToHsv
import eu.kanade.presentation.util.Screen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AdvancedCustomAccentScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val uiPreferences = remember { Injekt.get<UiPreferences>() }
        val customAccentSeedPref = remember { uiPreferences.customThemeAccentSeed() }
        val currentSeed = remember(customAccentSeedPref) { customAccentSeedPref.get() }
        val initialSeed = remember(currentSeed) {
            if (currentSeed == UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET) {
                0xFF1E88E5.toInt()
            } else {
                normalizeAccentSeed(currentSeed)
            }
        }

        val initialHsv = remember(initialSeed) { seedToHsv(initialSeed) }
        var hue by rememberSaveable(initialSeed) { mutableStateOf(initialHsv.hue) }
        var saturation by rememberSaveable(initialSeed) { mutableStateOf(initialHsv.saturation) }
        var value by rememberSaveable(initialSeed) { mutableStateOf(initialHsv.value) }

        val previewSeed = hsvToAccentSeed(hue, saturation, value)
        val previewHex = accentSeedToHex(previewSeed)
        val previewDescription = stringResource(
            MR.strings.pref_custom_theme_picker_preview_content_description,
            previewHex,
        )

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.pref_custom_theme_advanced_editor),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier.padding(contentPadding),
            ) {
                CustomThemeAccentEditor(
                    previewSeed = previewSeed,
                    previewHex = previewHex,
                    previewDescription = previewDescription,
                    hue = hue,
                    onHueChange = { hue = it },
                    hueDescription = stringResource(MR.strings.pref_custom_theme_picker_hue),
                    saturation = saturation,
                    onSaturationChange = { saturation = it },
                    saturationDescription = stringResource(MR.strings.pref_custom_theme_picker_saturation),
                    value = value,
                    onValueChange = { value = it },
                    valueDescription = stringResource(MR.strings.pref_custom_theme_picker_value),
                )

                Button(
                    onClick = {
                        val appliedSeed = normalizeAccentSeed(previewSeed)
                        customAccentSeedPref.set(appliedSeed)
                        uiPreferences.addCustomThemeRecentAccentSeed(appliedSeed)
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        navigator.pop()
                    },
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Text(text = stringResource(MR.strings.action_apply))
                }
                Button(
                    onClick = { navigator.pop() },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            }
        }
    }
}
