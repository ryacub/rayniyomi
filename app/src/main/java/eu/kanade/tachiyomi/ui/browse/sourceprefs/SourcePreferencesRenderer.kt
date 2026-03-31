package eu.kanade.tachiyomi.ui.browse.sourceprefs

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import eu.kanade.presentation.more.settings.widget.ListPreferenceWidget
import eu.kanade.presentation.more.settings.widget.PreferenceGroupHeader
import eu.kanade.presentation.more.settings.widget.SwitchPreferenceWidget
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.data.preference.SharedPreferencesDataStore
import logcat.LogPriority
import logcat.logcat
import tachiyomi.i18n.MR
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
                        SourcePreferenceMultiSelectDialog(
                            preference = preference,
                            title = preference.title?.toString().orEmpty(),
                            subtitle = summary,
                        )
                    }

                    is EditTextPreference -> {
                        SourcePreferenceEditTextDialog(
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
