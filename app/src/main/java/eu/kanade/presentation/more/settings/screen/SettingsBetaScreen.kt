package eu.kanade.presentation.more.settings.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.settings.BetaPreferences
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsBetaScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_beta

    @Composable
    override fun getPreferences(): List<Preference> {
        val betaPreferences = remember { Injekt.get<BetaPreferences>() }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = betaPreferences.enableExperimentalComposeSettings(),
                title = stringResource(MR.strings.pref_enable_experimental_compose_settings),
                subtitle = stringResource(MR.strings.pref_experimental_compose_settings_summary),
            ),
        )
    }
}
