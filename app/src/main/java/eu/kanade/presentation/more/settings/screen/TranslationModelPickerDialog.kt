package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import eu.kanade.tachiyomi.data.translation.catalog.TranslationCatalogResult
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCatalogRepository
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelChoice
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelChoiceType
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelEntry
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelPickerState
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelPricingFormatter
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelResolution
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelResolver
import kotlinx.coroutines.launch
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TranslationModelPickerDialog(
    repository: TranslationModelCatalogRepository,
    provider: TranslationProvider,
    apiKey: String,
    modelPreference: Preference<String>,
    modelChoiceTypePreference: Preference<TranslationModelChoiceType>,
    onDismiss: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var pickerState by remember { mutableStateOf(TranslationModelPickerState(isLoading = true)) }

    fun resolveAutomaticModel(visibleModels: List<TranslationModelEntry>) {
        if (modelChoiceTypePreference.get() != TranslationModelChoiceType.AUTOMATIC) return
        val resolution = TranslationModelResolver.resolve(
            provider = provider,
            choice = TranslationModelChoice(TranslationModelChoiceType.AUTOMATIC),
            models = visibleModels,
        )
        if (resolution is TranslationModelResolution.Selected) {
            modelPreference.set(resolution.model.id)
        } else {
            modelPreference.set("")
        }
    }

    suspend fun loadModels(forceRefresh: Boolean) {
        pickerState = pickerState.copy(isLoading = true)
        try {
            when (val result = repository.load(provider, apiKey, forceRefresh)) {
                is TranslationCatalogResult.Success -> {
                    pickerState = TranslationModelPickerState.fromResult(result, provider)
                    resolveAutomaticModel(pickerState.models)
                }
                is TranslationCatalogResult.Failure -> {
                    pickerState = TranslationModelPickerState.fromResult(result, provider)
                }
            }
        } finally {
            pickerState = pickerState.copy(isLoading = false)
        }
    }

    LaunchedEffect(repository) { loadModels(forceRefresh = false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.pref_translation_model_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pickerState.isLoading) {
                    Text(stringResource(AYMR.strings.pref_translation_model_loading))
                }
                pickerState.errorMessage?.let {
                    Text(stringResource(AYMR.strings.pref_translation_model_refresh_failed))
                }
                if (provider == TranslationProvider.OPENROUTER) {
                    Text(stringResource(AYMR.strings.pref_translation_model_automatic_paid_warning))
                }
                if (!pickerState.isLoading && pickerState.models.isEmpty()) {
                    Text(stringResource(AYMR.strings.pref_translation_model_empty))
                }
                LazyColumn {
                    item {
                        TextButton(
                            onClick = {
                                modelChoiceTypePreference.set(TranslationModelChoiceType.AUTOMATIC)
                                resolveAutomaticModel(pickerState.models)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(AYMR.strings.pref_translation_model_automatic))
                        }
                    }
                    items(pickerState.models) { model ->
                        TextButton(
                            onClick = {
                                modelPreference.set(model.id)
                                modelChoiceTypePreference.set(TranslationModelChoiceType.PINNED)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column {
                                Text(model.displayName)
                                Text(model.id)
                                Text(
                                    listOfNotNull(
                                        model.cost.name.lowercase(),
                                        model.stability.name.lowercase(),
                                        model.dataTerms,
                                        TranslationModelPricingFormatter.format(model),
                                        model.capabilities.inputModalities.takeIf { it.isNotEmpty() }
                                            ?.let { "input: ${it.joinToString()}" },
                                        model.capabilities.outputModalities.takeIf { it.isNotEmpty() }
                                            ?.let { "output: ${it.joinToString()}" },
                                        model.capabilities.maxOutputTokens?.let { "$it output tokens" },
                                    ).joinToString(" · "),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    coroutineScope.launch { loadModels(forceRefresh = true) }
                },
                enabled = !pickerState.isLoading,
            ) {
                Text(stringResource(AYMR.strings.pref_translation_model_refresh))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}
