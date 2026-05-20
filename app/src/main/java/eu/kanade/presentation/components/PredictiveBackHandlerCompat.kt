package eu.kanade.presentation.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.collect

@Composable
fun PredictiveBackHandlerCompat(
    enabled: Boolean = true,
    onBack: () -> Unit,
) {
    PredictiveBackHandler(enabled = enabled) { progress ->
        progress.collect { }
        onBack()
    }
}
