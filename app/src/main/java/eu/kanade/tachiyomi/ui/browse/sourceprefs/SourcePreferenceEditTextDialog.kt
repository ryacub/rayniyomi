package eu.kanade.tachiyomi.ui.browse.sourceprefs

import android.text.InputType
import android.widget.EditText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import eu.kanade.presentation.more.settings.widget.TextPreferenceWidget
import eu.kanade.tachiyomi.widget.TachiyomiTextInputEditText
import eu.kanade.tachiyomi.widget.TachiyomiTextInputEditText.Companion.setIncognito
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal data class SourcePreferenceEditTextUiModel(
    val title: String,
    val subtitle: String?,
    val dialogTitle: String?,
    val currentValue: String,
    val enabled: Boolean,
    val onBindEditText: ((EditText) -> Unit)? = null,
)

@Composable
internal fun SourcePreferenceEditTextDialog(
    model: SourcePreferenceEditTextUiModel,
    onValueConfirmed: (String) -> Boolean,
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by rememberSaveable { mutableStateOf(model.currentValue) }

    val clearDescription = stringResource(MR.strings.pref_source_preference_clear_text)
    val inputDescriptionFormat = stringResource(MR.strings.pref_source_preference_input_a11y)
    val dialogTitleFallback = stringResource(MR.strings.pref_source_preference_dialog_title_fallback)
    val invalidInputMessage = stringResource(MR.strings.pref_source_preference_invalid_input)
    val noneText = stringResource(MR.strings.none)
    val scope = rememberCoroutineScope()

    TextPreferenceWidget(
        modifier = Modifier.semantics {
            contentDescription = buildString {
                append(model.title)
                append(", ")
                append(model.subtitle ?: model.currentValue)
            }
        },
        title = model.title,
        subtitle = model.subtitle,
        onPreferenceClick = {
            if (model.enabled) {
                draft = model.currentValue
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
    val dialogTitle = model.dialogTitle
        ?.takeUnless { it.isBlank() }
        ?: model.title.takeUnless { it.isBlank() }
        ?: dialogTitleFallback

    AlertDialog(
        onDismissRequest = { showDialog = false },
        title = { Text(text = dialogTitle) },
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
                            contentDescription = inputDescriptionFormat.format(model.title, draft)
                            stateDescription = draft.ifBlank { noneText }
                            if (!model.enabled) disabled()
                            validationError?.let { error(it) }
                        },
                    factory = { context ->
                        TachiyomiTextInputEditText(context).apply {
                            inputType = InputType.TYPE_CLASS_TEXT
                            setIncognito(scope)
                            model.onBindEditText?.invoke(this)
                            setText(model.currentValue)
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
                TextButton(
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
                    Text(text = clearDescription)
                }
                validationError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier
                    .focusRequester(confirmFocus)
                    .focusProperties {
                        next = cancelFocus
                        down = cancelFocus
                    },
                onClick = {
                    val candidate = editTextRef?.text?.toString().orEmpty()
                    if (onValueConfirmed(candidate)) {
                        validationError = null
                        showDialog = false
                    } else {
                        validationError = invalidInputMessage
                    }
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(
                modifier = Modifier.focusRequester(cancelFocus),
                onClick = {
                    validationError = null
                    showDialog = false
                },
            ) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )

    LaunchedEffect(showDialog) {
        if (showDialog) inputFocus.requestFocus()
    }
}
