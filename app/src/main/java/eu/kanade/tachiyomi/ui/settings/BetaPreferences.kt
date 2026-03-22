package eu.kanade.tachiyomi.ui.settings

import tachiyomi.core.common.preference.PreferenceStore

class BetaPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun enableExperimentalComposeSettings() = preferenceStore.getBoolean(
        "beta_enable_experimental_compose_settings",
        false,
    )

    fun enableExperimentalThemingSettings() = preferenceStore.getBoolean(
        "beta_enable_experimental_theming_settings",
        false,
    )
}
