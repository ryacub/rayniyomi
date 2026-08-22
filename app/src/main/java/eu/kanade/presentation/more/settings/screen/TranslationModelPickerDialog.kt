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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.data.translation.catalog.TranslationCatalogResult
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelChoiceType
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelCatalogRepository
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelEntry
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import tachiyomi.core.common.preference.Preference
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun TranslationModelPickerDialog(
    repository: TranslationModelCatalogRepository,
    modelPreference: Preference<String>,
    modelChoiceTypePreference: Preference<TranslationModelChoiceType>,
    onDismiss: () -> Unit,
) {
    var models by remember { mutableStateOf<List<TranslationModelEntry>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun loadModels(forceRefresh: Boolean) {
        isLoading = true
        when (val result = repository.load(TranslationProvider.OPENROUTER, forceRefresh)) {
            is TranslationCatalogResult.Success -> {
                models = result.catalog.compatibleModels()
                errorMessage = null
            }
            is TranslationCatalogResult.Failure -> {
                models = result.cachedModels
                errorMessage = result.reason
            }
        }
        isLoading = false
    }

    LaunchedEffect(repository) { loadModels(forceRefresh = false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.pref_translation_model_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLoading) {
                    Text(stringResource(AYMR.strings.pref_translation_model_loading))
                }
                errorMessage?.let {
                    Text(stringResource(AYMR.strings.pref_translation_model_refresh_failed))
                }
                LazyColumn {
                    items(models) { model ->
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
                onClick = { loadModels(forceRefresh = true) },
            ) {
                Text(stringResource(AYMR.strings.pref_translation_model_refresh))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}
