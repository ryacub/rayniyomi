package eu.kanade.tachiyomi.ui.player.cast

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun CastSubtitleWarningDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(AYMR.strings.cast_subtitle_unavailable_title)) },
        text = { Text(stringResource(AYMR.strings.cast_subtitle_unavailable_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(AYMR.strings.cast_subtitle_cast_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(AYMR.strings.cast_watch_locally))
            }
        },
    )
}
