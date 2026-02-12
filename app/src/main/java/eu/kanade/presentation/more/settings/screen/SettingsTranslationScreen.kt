package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_category_translation

    @Composable
    override fun getPreferences(): List<Preference> {
        val translationPreferences = remember { Injekt.get<TranslationPreferences>() }

        val provider by translationPreferences.translationProvider().collectAsState()
        val apiKey by translationPreferences.translationApiKey().collectAsState()

        return listOf(
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translationProvider(),
                entries = TranslationProvider.entries
                    .associateWith { it.displayName }
                    .toImmutableMap(),
                title = stringResource(AYMR.strings.pref_translation_provider),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = translationPreferences.translationApiKey(),
                title = stringResource(AYMR.strings.pref_translation_api_key),
                subtitle = if (apiKey.isBlank()) {
                    stringResource(AYMR.strings.pref_translation_api_key_not_set)
                } else {
                    stringResource(AYMR.strings.pref_translation_api_key_set)
                },
                enabled = provider != TranslationProvider.NONE,
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = translationPreferences.targetLanguage(),
                title = stringResource(AYMR.strings.pref_translation_target_language),
                subtitle = translationPreferences.targetLanguage().get(),
                enabled = provider != TranslationProvider.NONE,
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = translationPreferences.translationModel(),
                title = stringResource(AYMR.strings.pref_translation_model),
                subtitle = stringResource(AYMR.strings.pref_translation_model_summary),
                enabled = provider != TranslationProvider.NONE,
            ),
        )
    }
}
