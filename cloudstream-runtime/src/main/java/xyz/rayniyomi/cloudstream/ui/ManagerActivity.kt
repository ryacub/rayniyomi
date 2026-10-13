package xyz.rayniyomi.cloudstream.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import xyz.rayniyomi.cloudstream.ManagerApplication

class ManagerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as ManagerApplication
        setContent {
            ManagerTheme {
                val model = viewModel { PluginManagerViewModel(app.manager, app.store, Dispatchers.IO) }
                val state by model.state.collectAsStateWithLifecycle()
                ManagerScreen(
                    state = state,
                    actions = ManagerActions(
                        giveConsent = model::giveConsent,
                        addRepository = model::addRepository,
                        removeRepository = model::removeRepository,
                        refresh = model::refresh,
                        install = model::install,
                        updateAll = model::updateAll,
                        setEnabled = model::setEnabled,
                        remove = model::remove,
                        dismissMessage = model::dismissMessage,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ManagerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}
