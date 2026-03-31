package eu.kanade.tachiyomi.ui.browse.sourceprefs

import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource

internal data class SourcePreferenceMultiSelectUiModel(
    val title: String,
    val subtitle: String?,
    val entries: Map<String, String>,
    val selectedValues: Set<String>,
    val enabled: Boolean,
)

@Composable
internal fun SourcePreferenceMultiSelectDialog(
    model: SourcePreferenceMultiSelectUiModel,
    onValuesConfirmed: (Set<String>) -> Unit,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val selected = remember(showDialog) { mutableStateListOf<String>() }

    val currentSummary = model.subtitle
        ?: model.selectedValues
            .mapNotNull { model.entries[it] }
            .joinToString()
            .takeUnless { it.isBlank() }
        ?: stringResource(MR.strings.none)

    TextPreferenceWidget(
        modifier = Modifier.semantics {
            role = Role.Button
            contentDescription = "${model.title}, $currentSummary"
        },
        title = model.title,
        subtitle = currentSummary,
        onPreferenceClick = {
            if (!model.enabled) return@TextPreferenceWidget
            selected.clear()
            selected.addAll(model.selectedValues)
            showDialog = true
        },
    )

    if (!showDialog) return

    AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text(text = model.title) },
        text = {
            ScrollbarLazyColumn {
                items(model.entries.entries.toList(), key = { it.key }) { (key, value) ->
                    LabeledCheckbox(
                        label = value,
                        checked = selected.contains(key),
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (!selected.contains(key)) selected.add(key)
                            } else {
                                selected.remove(key)
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onValuesConfirmed(selected.toSet())
                    showDialog = false
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = { showDialog = false }) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}
