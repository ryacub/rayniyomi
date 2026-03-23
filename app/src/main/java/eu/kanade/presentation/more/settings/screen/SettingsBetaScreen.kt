package eu.kanade.presentation.more.settings.screen

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.settings.BetaFeature
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

        return betaSettings.map { setting ->
            Preference.PreferenceItem.SwitchPreference(
                preference = betaPreferences.feature(setting.feature),
                title = stringResource(setting.titleRes),
                subtitle = stringResource(setting.summaryRes),
            )
        }
    }

    private data class BetaSetting(
        val feature: BetaFeature,
        val titleRes: StringResource,
        val summaryRes: StringResource,
    )

    private val betaSettings = listOf(
        BetaSetting(
            feature = BetaFeature.EXPERIMENTAL_COMPOSE_SETTINGS,
            titleRes = MR.strings.pref_enable_experimental_compose_settings,
            summaryRes = MR.strings.pref_experimental_compose_settings_summary,
        ),
        BetaSetting(
            feature = BetaFeature.EXPERIMENTAL_THEMING_SETTINGS,
            titleRes = MR.strings.pref_enable_experimental_theming_settings,
            summaryRes = MR.strings.pref_experimental_theming_settings_summary,
        ),
    )
}
