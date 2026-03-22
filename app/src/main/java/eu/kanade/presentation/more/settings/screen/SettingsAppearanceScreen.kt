package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.domain.ui.model.setAppCompatDelegateThemeMode
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.appearance.AdvancedCustomAccentScreen
import eu.kanade.presentation.more.settings.screen.appearance.AppLanguageScreen
import eu.kanade.presentation.more.settings.widget.AppThemeModePreferenceWidget
import eu.kanade.presentation.more.settings.widget.AppThemePreferenceWidget
import eu.kanade.presentation.more.settings.widget.CustomThemeAccentPreferenceWidget
import eu.kanade.presentation.more.settings.widget.CustomThemeColorPickerDialog
import eu.kanade.presentation.more.settings.widget.normalizeAccentSeed
import eu.kanade.tachiyomi.ui.settings.BetaPreferences
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

object SettingsAppearanceScreen : SearchableSettings {

    internal const val DEFAULT_CUSTOM_ACCENT_PICKER_SEED = 0xFF1E88E5.toInt()

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_appearance

    @Composable
    override fun getPreferences(): List<Preference> {
        val uiPreferences = remember { Injekt.get<UiPreferences>() }

        return listOf(
            getThemeGroup(uiPreferences = uiPreferences),
            getDisplayGroup(uiPreferences = uiPreferences),
        )
    }

    @Composable
    private fun getThemeGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val themeModePref = uiPreferences.themeMode()
        val themeMode by themeModePref.collectAsStateWithLifecycle()

        val appThemePref = uiPreferences.appTheme()
        val appTheme by appThemePref.collectAsStateWithLifecycle()
        val customAccentSeedPref = uiPreferences.customThemeAccentSeed()
        val customAccentSeed by customAccentSeedPref.collectAsStateWithLifecycle()

        val customThemeAccentSeedPref = uiPreferences.customThemeAccentSeed()
        val customThemeAccentSeed by customThemeAccentSeedPref.collectAsStateWithLifecycle()
        val recentAccentSeedsPref = uiPreferences.customThemeRecentAccentSeeds()
        val recentAccentSeeds by recentAccentSeedsPref.collectAsStateWithLifecycle()
        val betaPreferences = remember { Injekt.get<BetaPreferences>() }
        val experimentalThemingPref = betaPreferences.enableExperimentalThemingSettings()
        val experimentalThemingEnabled by experimentalThemingPref.collectAsStateWithLifecycle()

        val amoledPref = uiPreferences.themeDarkAmoled()
        val amoled by amoledPref.collectAsStateWithLifecycle()

        var showCustomAccentPicker by rememberSaveable { mutableStateOf(false) }
        var customAccentPickerSeed by rememberSaveable { mutableIntStateOf(DEFAULT_CUSTOM_ACCENT_PICKER_SEED) }
        var customAccentPickerSession by rememberSaveable { mutableIntStateOf(0) }
        var customAccentAccessibilityAnnouncement by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(experimentalThemingEnabled) {
            if (!experimentalThemingEnabled) {
                showCustomAccentPicker = false
            }
        }

        fun recreateForThemeChange() {
            (context as? Activity)?.let { ActivityCompat.recreate(it) }
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_theme),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.CustomPreference(
                    title = stringResource(MR.strings.pref_app_theme),
                ) {
                    Column {
                        AppThemeModePreferenceWidget(
                            value = themeMode,
                            onItemClick = {
                                themeModePref.set(it)
                                setAppCompatDelegateThemeMode(it)
                            },
                        )

                        AppThemePreferenceWidget(
                            value = appTheme,
                            amoled = amoled,
                            customAccentSeed = customAccentSeed,
                            onItemClick = { appThemePref.set(it) },
                            onCustomAccentSeedChange = { customAccentSeedPref.set(it) },
                        )

                        if (appTheme == AppTheme.CUSTOM && experimentalThemingEnabled) {
                            CustomThemeAccentPreferenceWidget(
                                selectedAccentSeed = customThemeAccentSeed,
                                recentAccentSeeds = recentAccentSeeds,
                                onSwatchClick = { selectedSeed ->
                                    customThemeAccentSeedPref.set(normalizeAccentSeed(selectedSeed))
                                    recreateForThemeChange()
                                },
                                onRecentColorClick = { selectedSeed ->
                                    customThemeAccentSeedPref.set(normalizeAccentSeed(selectedSeed))
                                    recreateForThemeChange()
                                },
                                onOpenPicker = {
                                    customAccentPickerSeed = resolveInitialCustomAccentPickerSeed(customThemeAccentSeed)
                                    customAccentPickerSession = nextCustomAccentPickerSession(customAccentPickerSession)
                                    showCustomAccentPicker = true
                                },
                                onOpenAdvancedEditor = { navigator.push(AdvancedCustomAccentScreen()) },
                                onReset = {
                                    customThemeAccentSeedPref.set(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET)
                                    recreateForThemeChange()
                                },
                                accessibilityAnnouncement = customAccentAccessibilityAnnouncement,
                                onSwatchAnnouncement = { announcement ->
                                    customAccentAccessibilityAnnouncement = announcement
                                },
                            )
                        }

                        if (experimentalThemingEnabled && showCustomAccentPicker) {
                            CustomThemeColorPickerDialog(
                                sessionKey = customAccentPickerSession,
                                initialSeed = customAccentPickerSeed,
                                onDismiss = { showCustomAccentPicker = false },
                                onApply = { pickedSeed ->
                                    customThemeAccentSeedPref.set(normalizeAccentSeed(pickedSeed))
                                    recreateForThemeChange()
                                },
                                onAppliedAnnouncement = { announcement ->
                                    customAccentAccessibilityAnnouncement = announcement
                                },
                            )
                        }
                    }
                },
                Preference.PreferenceItem.SwitchPreference(
                    preference = amoledPref,
                    title = stringResource(MR.strings.pref_dark_theme_pure_black),
                    enabled = themeMode != ThemeMode.LIGHT,
                    onValueChanged = {
                        (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        true
                    },
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.dynamicEntryCoverTheming(),
                    title = stringResource(MR.strings.pref_dynamic_entry_cover_theming),
                    subtitle = stringResource(MR.strings.pref_dynamic_entry_cover_theming_summary),
                ),
            ),
        )
    }

    @Composable
    private fun getDisplayGroup(
        uiPreferences: UiPreferences,
    ): Preference.PreferenceGroup {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val now = remember { LocalDate.now() }

        val dateFormat by uiPreferences.dateFormat().collectAsStateWithLifecycle()
        val formattedNow = remember(dateFormat) {
            UiPreferences.dateFormat(dateFormat).format(now)
        }

        return Preference.PreferenceGroup(
            title = stringResource(MR.strings.pref_category_display),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = stringResource(MR.strings.pref_app_language),
                    onClick = { navigator.push(AppLanguageScreen()) },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.tabletUiMode(),
                    entries = TabletUiMode.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_tablet_ui_mode),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.startScreen(),
                    entries = StartScreen.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = stringResource(AYMR.strings.pref_start_screen),
                    onValueChanged = {
                        context.toast(MR.strings.requires_app_restart)
                        true
                    },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.navStyle(),
                    entries = NavStyle.entries
                        .associateWith { stringResource(it.titleRes) }
                        .toImmutableMap(),
                    title = "Navigation Style",
                    onValueChanged = { true },
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = uiPreferences.dateFormat(),
                    entries = DateFormats
                        .associateWith {
                            val formattedDate = UiPreferences.dateFormat(it).format(now)
                            "${it.ifEmpty { stringResource(MR.strings.label_default) }} ($formattedDate)"
                        }
                        .toImmutableMap(),
                    title = stringResource(MR.strings.pref_date_format),
                ),
                Preference.PreferenceItem.SwitchPreference(
                    preference = uiPreferences.relativeTime(),
                    title = stringResource(MR.strings.pref_relative_format),
                    subtitle = stringResource(
                        MR.strings.pref_relative_format_summary,
                        stringResource(MR.strings.relative_time_today),
                        formattedNow,
                    ),
                ),
            ),
        )
    }
}

internal fun resolveInitialCustomAccentPickerSeed(currentAccentSeed: Int): Int {
    return if (currentAccentSeed == UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET) {
        SettingsAppearanceScreen.DEFAULT_CUSTOM_ACCENT_PICKER_SEED
    } else {
        normalizeAccentSeed(currentAccentSeed)
    }
}

internal fun nextCustomAccentPickerSession(currentSession: Int): Int = currentSession + 1

private val DateFormats = listOf(
    "", // Default
    "MM/dd/yy",
    "dd/MM/yy",
    "yyyy-MM-dd",
    "dd MMM yyyy",
    "MMM dd, yyyy",
)
