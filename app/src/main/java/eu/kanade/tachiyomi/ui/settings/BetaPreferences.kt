package eu.kanade.tachiyomi.ui.settings

import tachiyomi.core.common.preference.PreferenceStore

enum class BetaFeature(
    val preferenceKey: String,
    val defaultValue: Boolean = false,
) {
    EXPERIMENTAL_COMPOSE_SETTINGS(
        preferenceKey = "beta_enable_experimental_compose_settings",
    ),
    EXPERIMENTAL_THEMING_SETTINGS(
        preferenceKey = "beta_enable_experimental_theming_settings",
    ),
}

class BetaPreferences(
    private val preferenceStore: PreferenceStore,
) {

    fun feature(feature: BetaFeature) = preferenceStore.getBoolean(
        feature.preferenceKey,
        feature.defaultValue,
    )

    fun enableExperimentalComposeSettings() = preferenceStore.getBoolean(
        BetaFeature.EXPERIMENTAL_COMPOSE_SETTINGS.preferenceKey,
        BetaFeature.EXPERIMENTAL_COMPOSE_SETTINGS.defaultValue,
    )

    fun enableExperimentalThemingSettings() = preferenceStore.getBoolean(
        BetaFeature.EXPERIMENTAL_THEMING_SETTINGS.preferenceKey,
        BetaFeature.EXPERIMENTAL_THEMING_SETTINGS.defaultValue,
    )
}
