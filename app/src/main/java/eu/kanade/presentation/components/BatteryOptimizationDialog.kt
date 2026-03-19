package eu.kanade.presentation.components

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Dialog shown when the user queues 10+ items for download and the app is subject
 * to battery optimization. Prompts user to exempt the app from battery optimization.
 */
@Composable
fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(MR.strings.battery_optimization_title))
        },
        text = {
            Text(text = stringResource(MR.strings.battery_optimization_description))
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openBatteryOptimizationSettings(context)
                onDismiss()
            }) {
                Text(text = stringResource(MR.strings.battery_optimization_settings))
            }
        },
    )
}

/**
 * Opens the battery optimization settings for the app.
 */
fun openBatteryOptimizationSettings(context: Context) {
    val packageName = context.packageName
    val intent = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
        }

        else -> {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to general battery settings if specific intent fails
        context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
    }
}
