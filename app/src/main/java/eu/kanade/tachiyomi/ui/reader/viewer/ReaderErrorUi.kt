package eu.kanade.tachiyomi.ui.reader.viewer

import android.view.MotionEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal data class ReaderErrorUiState(
    val showOpenInWebView: Boolean,
)

internal data class ReaderErrorUiActions(
    val onRetry: () -> Unit,
    val onOpenInWebView: () -> Unit,
    val onActionPressChanged: (Boolean) -> Unit = {},
)

internal fun canOpenReaderPageInWebView(imageUrl: String?): Boolean {
    return imageUrl?.startsWith("http", ignoreCase = true) == true
}

@Composable
internal fun ReaderErrorSurface(
    state: ReaderErrorUiState,
    actions: ReaderErrorUiActions,
) {
    val headingFocusRequester = FocusRequester()
    val retryFocusRequester = FocusRequester()
    val openWebFocusRequester = FocusRequester()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(MR.strings.decode_image_error),
            modifier = Modifier
                .semantics { heading() }
                .focusRequester(headingFocusRequester)
                .focusProperties {
                    next = retryFocusRequester
                }
                .focusable(),
        )
        Button(
            onClick = actions.onRetry,
            modifier = Modifier
                .padding(top = 8.dp)
                .focusRequester(retryFocusRequester)
                .focusProperties {
                    previous = headingFocusRequester
                    if (state.showOpenInWebView) {
                        next = openWebFocusRequester
                    }
                }
                .pointerInteropFilter { event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> actions.onActionPressChanged(true)
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> actions.onActionPressChanged(false)
                    }
                    false
                }
                .focusable(),
        ) {
            Text(stringResource(MR.strings.action_retry))
        }
        if (state.showOpenInWebView) {
            Button(
                onClick = actions.onOpenInWebView,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .focusRequester(openWebFocusRequester)
                    .focusProperties {
                        previous = retryFocusRequester
                    }
                    .pointerInteropFilter { event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> actions.onActionPressChanged(true)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> actions.onActionPressChanged(false)
                        }
                        false
                    }
                    .focusable(),
            ) {
                Text(stringResource(MR.strings.action_open_in_web_view))
            }
        }
    }
}
