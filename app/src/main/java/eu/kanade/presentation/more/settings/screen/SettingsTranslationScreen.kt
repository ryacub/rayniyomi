package eu.kanade.presentation.more.settings.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCatalogRepository
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelChoiceType
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import kotlinx.collections.immutable.toImmutableMap
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTranslationScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_category_translation_byok

    @Composable
    override fun getPreferences(): List<Preference> {
        val translationPreferences = remember { Injekt.get<TranslationPreferences>() }
        val uriHandler = LocalUriHandler.current

        val provider by translationPreferences.translationProvider().collectAsStateWithLifecycle()
        val apiKeyPreference = remember(provider) { translationPreferences.translationApiKey(provider) }
        val modelPreference = remember(provider) { translationPreferences.translationModel(provider) }
        val apiKey by apiKeyPreference.collectAsStateWithLifecycle()
        val model by modelPreference.collectAsStateWithLifecycle()
        val modelChoiceTypePreference = remember(provider) {
            translationPreferences.translationModelChoiceType(provider)
        }
        val modelChoiceType by modelChoiceTypePreference.collectAsStateWithLifecycle()
        var showModelPicker by remember { mutableStateOf(false) }
        val catalogRepository = remember { TranslationModelCatalogRepository() }
        var showClearConfirmation by remember { mutableStateOf(false) }

        if (showClearConfirmation) {
            AlertDialog(
                onDismissRequest = { showClearConfirmation = false },
                title = {
                    Text(stringResource(AYMR.strings.pref_translation_clear_key_title, provider.displayName))
                },
                text = { Text(stringResource(AYMR.strings.pref_translation_clear_key_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            apiKeyPreference.delete()
                            showClearConfirmation = false
                        },
                    ) {
                        Text(stringResource(MR.strings.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirmation = false }) {
                        Text(stringResource(MR.strings.action_cancel))
                    }
                },
            )
        }

        val preferences = mutableListOf<Preference>(
            Preference.PreferenceItem.InfoPreference(
                title = stringResource(AYMR.strings.pref_translation_byok_notice),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = translationPreferences.translationProvider(),
                entries = TranslationProvider.entries
                    .associateWith { it.displayName }
                    .toImmutableMap(),
                title = stringResource(AYMR.strings.pref_translation_provider),
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = apiKeyPreference,
                isSecret = true,
                title = stringResource(AYMR.strings.pref_translation_api_key_byok),
                subtitle = if (apiKey.isBlank()) {
                    stringResource(AYMR.strings.pref_translation_api_key_not_set)
                } else {
                    stringResource(AYMR.strings.pref_translation_api_key_set)
                },
                enabled = provider != TranslationProvider.NONE,
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.pref_translation_clear_key, provider.displayName),
                enabled = provider != TranslationProvider.NONE && apiKey.isNotBlank(),
                onClick = { showClearConfirmation = true },
            ),
            Preference.PreferenceItem.EditTextPreference(
                preference = translationPreferences.targetLanguage(),
                title = stringResource(AYMR.strings.pref_translation_target_language),
                subtitle = translationPreferences.targetLanguage().get(),
                enabled = provider != TranslationProvider.NONE,
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.pref_translation_model),
                subtitle = when (modelChoiceType) {
                    TranslationModelChoiceType.AUTOMATIC ->
                        stringResource(AYMR.strings.pref_translation_model_automatic)
                    TranslationModelChoiceType.PINNED -> if (model.isBlank()) {
                        stringResource(AYMR.strings.pref_translation_model_none)
                    } else {
                        model
                    }
                },
                enabled = provider == TranslationProvider.OPENROUTER && apiKey.isNotBlank(),
                onClick = { showModelPicker = true },
            ),
        )

        if (showModelPicker) {
            TranslationModelPickerDialog(
                repository = catalogRepository,
                modelPreference = modelPreference,
                modelChoiceTypePreference = modelChoiceTypePreference,
                onDismiss = { showModelPicker = false },
            )
        }

        provider.links?.let { links ->
            preferences += Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.pref_translation_provider_key_link, provider.displayName),
                onClick = { uriHandler.openUri(links.key) },
            )
            preferences += Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.pref_translation_provider_usage_link, provider.displayName),
                onClick = { uriHandler.openUri(links.usage) },
            )
            preferences += Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.pref_translation_provider_billing_link, provider.displayName),
                onClick = { uriHandler.openUri(links.billing) },
            )
            preferences += Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.pref_translation_provider_revoke_link, provider.displayName),
                onClick = { uriHandler.openUri(links.revocation) },
            )
        }

        return preferences
    }
}
