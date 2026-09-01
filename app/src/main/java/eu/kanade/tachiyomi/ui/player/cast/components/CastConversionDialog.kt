package eu.kanade.tachiyomi.ui.player.cast.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.cast.CastConversionState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CastConversionDialog(
    state: CastConversionState,
    onConvert: (alwaysConvert: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    when (state) {
        CastConversionState.Idle -> Unit
        is CastConversionState.Prompt -> PromptDialog(
            container = state.container,
            onConvert = onConvert,
            onCancel = onCancel,
        )
        is CastConversionState.Converting -> ProgressDialog(
            progress = state.progress,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun PromptDialog(
    container: String,
    onConvert: (alwaysConvert: Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var alwaysConvert by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(AYMR.strings.cast_convert_title)) },
        text = {
            Column {
                Text(stringResource(AYMR.strings.cast_convert_message, container))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { alwaysConvert = !alwaysConvert }
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = alwaysConvert,
                        onCheckedChange = { alwaysConvert = it },
                    )
                    Text(stringResource(AYMR.strings.cast_convert_always))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConvert(alwaysConvert) }) {
                Text(stringResource(AYMR.strings.cast_convert_once))
            }
        },
    )
}

@Composable
private fun ProgressDialog(
    progress: Int?,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(AYMR.strings.cast_convert_title)) },
        text = {
            Column {
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(AYMR.strings.cast_conversion_progress, progress),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
