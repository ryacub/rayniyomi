package eu.kanade.presentation.more.settings.screen.translation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.data.translation.TranslationProvider
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelChoiceType
import eu.kanade.tachiyomi.data.translation.catalog.TranslationModelPickerState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun TranslationModelPickerContent(
    state: TranslationModelPickerState,
    provider: TranslationProvider,
    choiceType: TranslationModelChoiceType,
    selectedModelId: String,
    expandedModelIds: Set<String>,
    contentPadding: PaddingValues,
    onSelectAutomatic: () -> Unit,
    onSelectModel: (String) -> Unit,
    onToggleDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        if (state.isLoading && state.models.isEmpty()) {
            LoadingScreen()
            return
        }

        BannerBlock(state = state, provider = provider)

        if (!state.isLoading && state.models.isEmpty()) {
            EmptyScreen(
                stringRes = AYMR.strings.pref_translation_model_empty,
                modifier = Modifier.fillMaxWidth(),
            )
            return
        }

        LazyColumn(contentPadding = contentPadding) {
            item(key = "automatic") {
                TranslationModelPickerRowUi(
                    title = stringResource(AYMR.strings.pref_translation_model_automatic),
                    summary = stringResource(AYMR.strings.pref_translation_model_automatic_summary),
                    details = emptyList(),
                    selected = choiceType == TranslationModelChoiceType.AUTOMATIC,
                    expanded = false,
                    onSelect = onSelectAutomatic,
                    onToggleDetails = null,
                )
            }
            items(state.models, key = { it.id }) { model ->
                val row = TranslationModelRowUiFactory.create(model)
                TranslationModelPickerRowUi(
                    title = row.title,
                    summary = row.summary.toSummaryText(),
                    details = row.details.toDetailText(),
                    selected = choiceType == TranslationModelChoiceType.PINNED && model.id == selectedModelId,
                    expanded = model.id in expandedModelIds,
                    onSelect = { onSelectModel(model.id) },
                    onToggleDetails = { onToggleDetails(model.id) },
                )
            }
        }
    }
}

@Composable
private fun BannerBlock(
    state: TranslationModelPickerState,
    provider: TranslationProvider,
) {
    val padding = MaterialTheme.padding
    Column(
        Modifier.padding(start = padding.medium, end = padding.medium, bottom = padding.small),
    ) {
        state.errorMessage?.let {
            Text(
                text = stringResource(AYMR.strings.pref_translation_model_refresh_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = padding.small),
            )
            if (state.models.isNotEmpty()) {
                Text(
                    text = stringResource(AYMR.strings.pref_translation_model_cached_notice),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = padding.small),
                )
            }
        }
        if (provider == TranslationProvider.OPENROUTER) {
            Text(
                text = stringResource(AYMR.strings.pref_translation_model_automatic_paid_warning),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = padding.small),
            )
        }
        if (state.isLoading && state.models.isNotEmpty()) {
            LinearProgressIndicator(
                Modifier
                    .fillMaxWidth()
                    .padding(top = padding.small),
            )
        }
    }
}

@Composable
private fun TranslationModelPickerRowUi(
    title: String,
    summary: String?,
    details: List<String>,
    selected: Boolean,
    expanded: Boolean,
    onSelect: () -> Unit,
    onToggleDetails: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            supportingContent = summary?.let {
                {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = {
                RadioButton(selected = selected, onClick = null)
            },
            trailingContent = onToggleDetails?.let { onClick ->
                {
                    IconButton(onClick = onClick) {
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Outlined.ExpandLess
                            } else {
                                Icons.Outlined.ExpandMore
                            },
                            contentDescription = stringResource(
                                if (expanded) {
                                    AYMR.strings.pref_translation_model_details_hide
                                } else {
                                    AYMR.strings.pref_translation_model_details_show
                                },
                            ),
                        )
                    }
                }
            },
        )
        if (expanded && details.isNotEmpty()) {
            Column(Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp)) {
                details.forEach { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun List<TranslationModelRowUi.SummaryToken>.toSummaryText(): String? {
    if (isEmpty()) return null
    val parts = ArrayList<String>(size)
    for (token in this) {
        parts.add(
            when (token) {
                TranslationModelRowUi.SummaryToken.Free ->
                    stringResource(AYMR.strings.pref_translation_model_cost_free)
                TranslationModelRowUi.SummaryToken.Paid ->
                    stringResource(AYMR.strings.pref_translation_model_cost_paid)
                TranslationModelRowUi.SummaryToken.CostUnknown ->
                    stringResource(AYMR.strings.pref_translation_model_cost_unknown)
                // Stable is never produced by the factory (a stable model shows no stability
                // token) so it maps to an empty label and cannot surface in the UI.
                TranslationModelRowUi.SummaryToken.Stable -> ""
                TranslationModelRowUi.SummaryToken.StabilityUnknown ->
                    stringResource(AYMR.strings.pref_translation_model_stability_unknown)
                is TranslationModelRowUi.SummaryToken.MaxOutputTokens ->
                    stringResource(AYMR.strings.pref_translation_model_max_output_tokens, token.tokens)
            },
        )
    }
    return parts.joinToString(" · ")
}

@Composable
private fun List<TranslationModelRowUi.DetailToken>.toDetailText(): List<String> {
    val result = ArrayList<String>(size)
    for (token in this) {
        when (token) {
            is TranslationModelRowUi.DetailToken.ModelId ->
                result.add(stringResource(AYMR.strings.pref_translation_model_id, token.id))
            is TranslationModelRowUi.DetailToken.Pricing ->
                result.add(stringResource(AYMR.strings.pref_translation_model_pricing, token.text))
            is TranslationModelRowUi.DetailToken.InputModalities ->
                result.add(stringResource(AYMR.strings.pref_translation_model_input_modalities, token.values))
            is TranslationModelRowUi.DetailToken.OutputModalities ->
                result.add(stringResource(AYMR.strings.pref_translation_model_output_modalities, token.values))
            is TranslationModelRowUi.DetailToken.DataTerms ->
                result.add(stringResource(AYMR.strings.pref_translation_model_data_terms, token.text))
        }
    }
    return result
}

@PreviewLightDark
@Composable
private fun TranslationModelPickerContentPreview() {
    val models = TranslationModelPickerFixtures.forProvider(TranslationProvider.OPENROUTER)
    TachiyomiPreviewTheme {
        TranslationModelPickerContent(
            state = TranslationModelPickerState(models = models, isLoading = false),
            provider = TranslationProvider.OPENROUTER,
            choiceType = TranslationModelChoiceType.PINNED,
            selectedModelId = models[1].id,
            expandedModelIds = setOf(models[1].id),
            contentPadding = PaddingValues(16.dp),
            onSelectAutomatic = {},
            onSelectModel = {},
            onToggleDetails = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun TranslationModelPickerContentLongNamePreview() {
    TachiyomiPreviewTheme {
        TranslationModelPickerContent(
            state = TranslationModelPickerState(
                models = listOf(TranslationModelPickerFixtures.longNameModel()),
                isLoading = false,
            ),
            provider = TranslationProvider.CLAUDE,
            choiceType = TranslationModelChoiceType.PINNED,
            selectedModelId = TranslationModelPickerFixtures.longNameModel().id,
            expandedModelIds = emptySet(),
            contentPadding = PaddingValues(16.dp),
            onSelectAutomatic = {},
            onSelectModel = {},
            onToggleDetails = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun TranslationModelPickerContentErrorPreview() {
    val models = TranslationModelPickerFixtures.forProvider(TranslationProvider.CLAUDE)
    TachiyomiPreviewTheme {
        TranslationModelPickerContent(
            state = TranslationModelPickerState(
                models = models,
                isLoading = false,
                errorMessage = "boom",
            ),
            provider = TranslationProvider.CLAUDE,
            choiceType = TranslationModelChoiceType.AUTOMATIC,
            selectedModelId = "",
            expandedModelIds = emptySet(),
            contentPadding = PaddingValues(16.dp),
            onSelectAutomatic = {},
            onSelectModel = {},
            onToggleDetails = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun TranslationModelPickerContentEmptyPreview() {
    TachiyomiPreviewTheme {
        TranslationModelPickerContent(
            state = TranslationModelPickerState(models = emptyList(), isLoading = false),
            provider = TranslationProvider.CLAUDE,
            choiceType = TranslationModelChoiceType.AUTOMATIC,
            selectedModelId = "",
            expandedModelIds = emptySet(),
            contentPadding = PaddingValues(16.dp),
            onSelectAutomatic = {},
            onSelectModel = {},
            onToggleDetails = {},
        )
    }
}
