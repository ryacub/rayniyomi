package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun AppUpdatePromptDialog(
    versionName: String,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit,
    onSkipVersion: () -> Unit,
    onViewDetails: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(text = stringResource(MR.strings.update_check_notification_update_available))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = versionName, style = MaterialTheme.typography.titleMedium)
                Text(text = stringResource(MR.strings.update_prompt_dialog_message))
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onUpdateNow,
                ) {
                    Text(text = stringResource(MR.strings.update_check_confirm))
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onSkipVersion,
                    ) {
                        Text(text = stringResource(MR.strings.update_check_skip_version))
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onLater,
                    ) {
                        Text(text = stringResource(MR.strings.action_not_now))
                    }
                }
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onViewDetails,
                ) {
                    Text(text = stringResource(MR.strings.update_check_view_details))
                }
            }
        },
    )
}
