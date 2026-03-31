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
import androidx.preference.MultiSelectListPreference
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun SourcePreferenceMultiSelectDialog(
    preference: MultiSelectListPreference,
    title: String,
    subtitle: String?,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val selected = remember(showDialog) { mutableStateListOf<String>() }
    val entries = preference.toEntryMap()

    val currentSummary = subtitle
        ?: preference.values
            .mapNotNull { entries[it] }
            .joinToString()
            .takeUnless { it.isBlank() }
        ?: stringResource(MR.strings.none)

    TextPreferenceWidget(
        modifier = Modifier.semantics {
            role = Role.Button
            contentDescription = "$title, $currentSummary"
        },
        title = title,
        subtitle = currentSummary,
        onPreferenceClick = {
            if (!preference.isEnabled) return@TextPreferenceWidget
            selected.clear()
            selected.addAll(preference.values)
            showDialog = true
        },
    )

    if (!showDialog) return

    AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text(text = title) },
        text = {
            ScrollbarLazyColumn {
                items(entries.entries.toList(), key = { it.key }) { (key, value) ->
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
                    val next = selected.toSet()
                    if (preference.callChangeListener(next)) {
                        preference.values = next
                    }
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

private fun MultiSelectListPreference.toEntryMap(): Map<String, String> {
    val entries = entries ?: emptyArray()
    val values = entryValues ?: emptyArray()
    return buildMap {
        val size = minOf(entries.size, values.size)
        repeat(size) { index ->
            put(values[index].toString(), entries[index].toString())
        }
    }
}
