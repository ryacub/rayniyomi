package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun EditTextPreferenceWidget(
    title: String,
    subtitle: String?,
    dialogSubtitle: String? = null,
    icon: ImageVector?,
    value: String,
    isSecret: Boolean = false,
    onConfirm: suspend (String) -> Boolean,
    singleLine: Boolean = true,
    canBeBlank: Boolean = false,
    formatSubtitle: Boolean = true,
    validate: (String) -> Boolean = { true },
    errorMessage: @Composable ((String) -> String)? = null,
) {
    var isDialogShown by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        title = title,
        subtitle = if (formatSubtitle) subtitle?.format(value) else subtitle,
        icon = icon,
        onPreferenceClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        val scope = rememberCoroutineScope()
        val onDismissRequest = { isDialogShown = false }
        var textFieldValue by remember(value, isSecret) {
            mutableStateOf(TextFieldValue(if (isSecret) "" else value))
        }
        var isSecretHidden by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Column {
                    Text(text = title)
                    if (dialogSubtitle != null) {
                        Text(text = dialogSubtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            text = {
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    trailingIcon = {
                        if (isSecret) {
                            IconButton(onClick = { isSecretHidden = !isSecretHidden }) {
                                Icon(
                                    imageVector = if (isSecretHidden) {
                                        Icons.Filled.Visibility
                                    } else {
                                        Icons.Filled.VisibilityOff
                                    },
                                    contentDescription = stringResource(
                                        if (isSecretHidden) {
                                            MR.strings.action_show_text
                                        } else {
                                            MR.strings.action_hide_text
                                        },
                                    ),
                                )
                            }
                        } else if ((textFieldValue.text.isBlank() && !canBeBlank) ||
                            !validate(textFieldValue.text)
                        ) {
                            Icon(imageVector = Icons.Filled.Error, contentDescription = null)
                        } else {
                            IconButton(onClick = { textFieldValue = TextFieldValue("") }) {
                                Icon(
                                    imageVector = Icons.Filled.Cancel,
                                    contentDescription = stringResource(
                                        MR.strings.pref_source_preference_clear_text,
                                    ),
                                )
                            }
                        }
                    },
                    supportingText = {
                        if (!validate(textFieldValue.text) && errorMessage != null) {
                            Text(errorMessage(textFieldValue.text))
                        }
                    },
                    isError = (textFieldValue.text.isBlank() && !canBeBlank) || !validate(textFieldValue.text),
                    singleLine = singleLine,
                    visualTransformation = if (isSecret && isSecretHidden) {
                        PasswordVisualTransformation()
                    } else {
                        VisualTransformation.None
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isSecret) {
                            KeyboardType.Password
                        } else {
                            KeyboardType.Text
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmButton = {
                TextButton(
                    enabled =
                    textFieldValue.text != value &&
                        (textFieldValue.text.isNotBlank() || canBeBlank) &&
                        validate(textFieldValue.text),
                    onClick = {
                        scope.launch {
                            if (onConfirm(textFieldValue.text)) {
                                onDismissRequest()
                            }
                        }
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }
}
