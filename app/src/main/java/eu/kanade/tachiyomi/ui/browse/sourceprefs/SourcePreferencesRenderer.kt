package eu.kanade.tachiyomi.ui.browse.sourceprefs

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.widget.EditText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import androidx.preference.getOnBindEditTextListener
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.data.preference.SharedPreferencesDataStore
import eu.kanade.tachiyomi.widget.TachiyomiTextInputEditText
import eu.kanade.tachiyomi.widget.TachiyomiTextInputEditText.Companion.setIncognito
import logcat.LogPriority
import logcat.logcat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.i18n.stringResource

private data class PreferenceSection(
    val title: String?,
    val entries: List<Preference>,
)

fun buildSourcePreferenceScreen(
    context: Context,
    sourcePreferences: SharedPreferences,
    setup: (PreferenceScreen) -> Unit,
): PreferenceScreen {
    val preferenceManager = PreferenceManager(context)
    preferenceManager.preferenceDataStore = SharedPreferencesDataStore(sourcePreferences)

    val screen = preferenceManager.createPreferenceScreen(context)
    setup(screen)
    normalizePreferenceTree(screen)
    return screen
}

@Composable
fun SourcePreferencesContent(
    preferenceScreen: PreferenceScreen,
    sourcePreferences: SharedPreferences,
    contentPadding: PaddingValues,
) {
    val unavailableTemplate = stringResource(MR.strings.pref_source_preference_value_unavailable)
    val unsupportedSubtitleTemplate = stringResource(MR.strings.pref_source_preference_unsupported_type)
    val missingKey = stringResource(MR.strings.pref_source_preference_missing_key)
    val emptyTitle = stringResource(MR.strings.pref_source_preference_empty_title)
    val onText = stringResource(MR.strings.on)
    val offText = stringResource(MR.strings.off)

    var refreshSignal by rememberSaveable { mutableIntStateOf(0) }
    val listener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            refreshSignal++
        }
    }

    DisposableEffect(sourcePreferences) {
        sourcePreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sourcePreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val sections = remember(preferenceScreen, refreshSignal) { preferenceScreen.toSections() }

    ScrollbarLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        if (sections.none { it.entries.isNotEmpty() }) {
            item {
                TextPreferenceWidget(
                    title = emptyTitle,
                    onPreferenceClick = null,
                )
            }
            return@ScrollbarLazyColumn
        }

        sections.forEach { section ->
            if (!section.title.isNullOrBlank()) {
                item(key = "header_${section.title}") {
                    PreferenceGroupHeader(title = section.title)
                }
            }

            items(
                items = section.entries,
                key = { it.key ?: "pref_${it.hashCode()}" },
            ) { preference ->
                val summary = preference.summary?.toString()?.takeUnless { it.isBlank() }
                when (preference) {
                    is TwoStatePreference -> {
                        val checked = preference.isChecked
                        val state = if (checked) onText else offText
                        SwitchPreferenceWidget(
                            modifier = Modifier.semantics {
                                role = Role.Switch
                                stateDescription = state
                                contentDescription = buildString {
                                    append(preference.title?.toString().orEmpty())
                                    append(", ")
                                    append(state)
                                }
                            },
                            title = preference.title?.toString().orEmpty(),
                            subtitle = summary,
                            checked = checked,
                            onCheckedChanged = { newValue ->
                                if (preference.isEnabled && preference.callChangeListener(newValue)) {
                                    preference.isChecked = newValue
                                }
                            },
                        )
                    }

                    is ListPreference -> {
                        val entryMap = preference.toEntryMap()
                        val current = preference.value
                        ListPreferenceWidget(
                            value = current,
                            title = preference.title?.toString().orEmpty(),
                            subtitle = resolveListPreferenceSummary(
                                summary = summary,
                                currentValue = current,
                                entries = entryMap,
                                unavailableTemplate = unavailableTemplate,
                            ),
                            icon = null,
                            entries = entryMap,
                            onValueChange = { newValue ->
                                if (preference.isEnabled && preference.callChangeListener(newValue)) {
                                    preference.value = newValue
                                }
                            },
                        )
                    }

                    is MultiSelectListPreference -> {
                        MultiSelectPreferenceDialog(
                            preference = preference,
                            title = preference.title?.toString().orEmpty(),
                            subtitle = summary,
                        )
                    }

                    is EditTextPreference -> {
                        ExtensionEditTextPreferenceDialog(
                            preference = preference,
                            title = preference.title?.toString().orEmpty(),
                            subtitle = summary,
                            enabled = preference.isEnabled,
                        )
                    }

                    else -> {
                        val isGenericClickable =
                            preference.isSelectable || preference.intent != null ||
                                preference.onPreferenceClickListener != null
                        if (isGenericClickable) {
                            TextPreferenceWidget(
                                modifier = Modifier.semantics { role = Role.Button },
                                title = preference.title?.toString(),
                                subtitle = summary,
                                onPreferenceClick = {
                                    if (!preference.isEnabled) return@TextPreferenceWidget
                                    dispatchPreferenceClick(preference)
                                },
                            )
                        } else {
                            val fallbackTitle = preference.title?.toString()
                                ?.takeUnless { it.isBlank() }
                                ?: preference.javaClass.simpleName
                            TextPreferenceWidget(
                                modifier = Modifier.semantics {
                                    role = Role.Button
                                    contentDescription = fallbackTitle
                                },
                                title = fallbackTitle,
                                subtitle = formatUnsupportedTypeSubtitle(
                                    template = unsupportedSubtitleTemplate,
                                    typeName = preference.javaClass.simpleName,
                                    key = preference.key,
                                    keyFallback = missingKey,
                                ),
                                onPreferenceClick = null,
                            )
                            logcat(LogPriority.DEBUG) {
                                "Unsupported source preference rendered as informational row: " +
                                    "type=${preference.javaClass.name}, key=${preference.key ?: missingKey}"
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiSelectPreferenceDialog(
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

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { androidx.compose.material3.Text(text = title) },
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
            androidx.compose.material3.TextButton(
                onClick = {
                    val next = selected.toSet()
                    if (preference.callChangeListener(next)) {
                        preference.values = next
                    }
                    showDialog = false
                },
            ) {
                androidx.compose.material3.Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { showDialog = false }) {
                androidx.compose.material3.Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun ExtensionEditTextPreferenceDialog(
    preference: EditTextPreference,
    title: String,
    subtitle: String?,
    enabled: Boolean,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    val clearDescription = stringResource(MR.strings.pref_source_preference_clear_text)
    val inputDescriptionFormat = stringResource(MR.strings.pref_source_preference_input_a11y)
    val dialogTitleFallback = stringResource(MR.strings.pref_source_preference_dialog_title_fallback)
    val invalidInputMessage = stringResource(MR.strings.pref_source_preference_invalid_input)
    val noneText = stringResource(MR.strings.none)
    val scope = rememberCoroutineScope()

    TextPreferenceWidget(
        modifier = Modifier.semantics {
            role = Role.Button
            contentDescription = buildString {
                append(title)
                append(", ")
                append(subtitle ?: preference.text.orEmpty())
            }
        },
        title = title,
        subtitle = subtitle,
        onPreferenceClick = {
            if (enabled) {
                validationError = null
                showDialog = true
            }
        },
    )

    if (!showDialog) return

    val inputFocus = remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    var editTextRef by remember { mutableStateOf<EditText?>(null) }
    var draft by rememberSaveable { mutableStateOf(preference.text.orEmpty()) }
    val dialogTitle = preference.dialogTitle?.toString()
        ?.takeUnless { it.isBlank() }
        ?: title.takeUnless { it.isBlank() }
        ?: dialogTitleFallback

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { androidx.compose.material3.Text(text = dialogTitle) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFocus)
                        .focusProperties {
                            next = clearFocus
                            down = clearFocus
                        }
                        .semantics {
                            contentDescription = inputDescriptionFormat.format(title, draft)
                            stateDescription = draft.ifBlank { noneText }
                            if (!enabled) disabled()
                            validationError?.let { error(it) }
                        },
                    factory = { context ->
                        TachiyomiTextInputEditText(context).apply {
                            inputType = InputType.TYPE_CLASS_TEXT
                            setIncognito(scope)
                            preference.getOnBindEditTextListener()?.onBindEditText(this)
                            setText(preference.text.orEmpty())
                            setSelection(text?.length ?: 0)
                            doAfterTextChanged {
                                draft = it?.toString().orEmpty()
                                validationError = null
                            }
                            editTextRef = this
                        }
                    },
                    update = { editText ->
                        editTextRef = editText
                        if (editText.text?.toString() != draft) {
                            editText.setText(draft)
                            editText.setSelection(editText.text?.length ?: 0)
                        }
                    },
                )
                androidx.compose.material3.TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(clearFocus)
                        .focusProperties {
                            next = confirmFocus
                            down = confirmFocus
                        }
                        .semantics { contentDescription = clearDescription },
                    onClick = {
                        editTextRef?.setText("")
                        draft = ""
                        validationError = null
                    },
                ) {
                    androidx.compose.material3.Text(text = clearDescription)
                }
                validationError?.let {
                    androidx.compose.material3.Text(
                        text = it,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                modifier = Modifier
                    .focusRequester(confirmFocus)
                    .focusProperties {
                        next = cancelFocus
                        down = cancelFocus
                    },
                onClick = {
                    val candidate = editTextRef?.text?.toString().orEmpty()
                    if (preference.callChangeListener(candidate)) {
                        preference.text = candidate
                        validationError = null
                        showDialog = false
                    } else {
                        validationError = invalidInputMessage
                    }
                },
            ) {
                androidx.compose.material3.Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                modifier = Modifier.focusRequester(cancelFocus),
                onClick = {
                    validationError = null
                    showDialog = false
                },
            ) {
                androidx.compose.material3.Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )

    LaunchedEffect(showDialog) {
        if (showDialog) inputFocus.requestFocus()
    }
}

internal fun resolveListPreferenceSummary(
    summary: String?,
    currentValue: String?,
    entries: Map<String, String>,
    unavailableTemplate: String,
): String? {
    if (!summary.isNullOrBlank()) return summary
    if (currentValue.isNullOrBlank()) return null
    return entries[currentValue] ?: unavailableTemplate.format(currentValue)
}

internal fun formatUnsupportedTypeSubtitle(
    template: String,
    typeName: String,
    key: String?,
    keyFallback: String,
): String {
    val keyOrUnknown = key ?: keyFallback
    return template.format(typeName, keyOrUnknown)
}

private fun ListPreference.toEntryMap(): Map<String, String> {
    val entries = entries ?: emptyArray()
    val values = entryValues ?: emptyArray()
    return buildMap {
        val size = minOf(entries.size, values.size)
        repeat(size) { index ->
            put(values[index].toString(), entries[index].toString())
        }
    }
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

private fun normalizePreferenceTree(group: PreferenceGroup) {
    repeat(group.preferenceCount) { index ->
        val pref = group.getPreference(index)
        pref.isIconSpaceReserved = false
        pref.isSingleLineTitle = false
        if (pref is DialogPreference && pref.dialogTitle.isNullOrBlank()) {
            pref.dialogTitle = pref.title
        }

        if (pref is PreferenceGroup) {
            normalizePreferenceTree(pref)
        }
    }
}

private fun dispatchPreferenceClick(preference: Preference) {
    preference.performClick()
}

private fun PreferenceScreen.toSections(): List<PreferenceSection> {
    val sections = mutableListOf<PreferenceSection>()

    fun collect(group: PreferenceGroup, title: String?) {
        val entries = mutableListOf<Preference>()
        repeat(group.preferenceCount) { index ->
            val pref = group.getPreference(index)
            if (!pref.isVisible) return@repeat

            if (pref is PreferenceCategory) {
                if (entries.isNotEmpty()) {
                    sections += PreferenceSection(title = title, entries = entries.toList())
                    entries.clear()
                }
                collect(pref, pref.title?.toString())
            } else if (pref is PreferenceGroup) {
                collect(pref, pref.title?.toString())
            } else {
                entries += pref
            }
        }

        if (entries.isNotEmpty()) {
            sections += PreferenceSection(title = title, entries = entries.toList())
        }
    }

    collect(this, null)
    return sections
}
