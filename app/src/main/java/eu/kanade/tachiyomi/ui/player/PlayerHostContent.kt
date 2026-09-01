package eu.kanade.tachiyomi.ui.player

import android.graphics.Rect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.viewinterop.AndroidView
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.ui.player.cast.CastSubtitleWarningDialog
import eu.kanade.tachiyomi.ui.player.controls.PlayerControls

internal data class PlayerHostUiCallbacks(
    val onBackPress: () -> Unit,
    val onPipRectChanged: (Rect) -> Unit,
    val onCastSubtitleWarningConfirm: () -> Unit,
    val onCastSubtitleWarningCancel: () -> Unit,
)

@Composable
internal fun PlayerHostContent(
    playerView: AniyomiMPVView,
    viewModel: PlayerViewModel,
    showCastSubtitleWarning: Boolean,
    callbacks: PlayerHostUiCallbacks,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { playerView },
        )
        TachiyomiTheme {
            PlayerControls(
                viewModel = viewModel,
                onBackPress = callbacks.onBackPress,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        val boundsInWindow = it.boundsInWindow()
                        val pipRect = Rect(
                            boundsInWindow.left.toInt(),
                            boundsInWindow.top.toInt(),
                            boundsInWindow.right.toInt(),
                            boundsInWindow.bottom.toInt(),
                        )
                        callbacks.onPipRectChanged(pipRect)
                    },
            )
            if (showCastSubtitleWarning) {
                CastSubtitleWarningDialog(
                    onConfirm = callbacks.onCastSubtitleWarningConfirm,
                    onCancel = callbacks.onCastSubtitleWarningCancel,
                )
            }
        }
    }
}
