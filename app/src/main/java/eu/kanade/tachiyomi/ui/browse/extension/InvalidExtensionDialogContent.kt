package eu.kanade.tachiyomi.ui.browse.extension

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun InvalidExtensionDialogContent(
    extensionName: String,
    reason: String,
    diagnostics: InvalidExtensionDiagnostics,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
    ) {
        Text(text = stringResource(MR.strings.ext_invalid_extension_message, extensionName))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(MR.strings.ext_invalid_extension_reason, reason),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(MR.strings.ext_invalid_extension_package, diagnostics.pkgName),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(MR.strings.ext_invalid_extension_version, diagnostics.version),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                MR.strings.ext_invalid_extension_signature,
                diagnostics.signatureHashDisplay,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        diagnostics.debugDetail?.let {
            Text(
                text = stringResource(MR.strings.ext_invalid_extension_detail, it),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = stringResource(MR.strings.ext_invalid_extension_recovery_hint))
    }
}
