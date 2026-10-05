package eu.kanade.tachiyomi.ui.reader.viewer

import android.view.MotionEvent
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.util.SourceErrorAction
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

internal data class ReaderErrorUiState(
    val showOpenInWebView: Boolean,
    val showMigrate: Boolean,
)

internal data class ReaderErrorUiActions(
    val onRetry: () -> Unit,
    val onOpenInWebView: () -> Unit,
    val onHelp: () -> Unit,
    val onMigrate: () -> Unit,
    val onActionPressChanged: (Boolean) -> Unit = {},
)

internal fun canOpenReaderPageInWebView(imageUrl: String?): Boolean {
    return imageUrl?.startsWith("http", ignoreCase = true) == true
}

internal fun readerErrorActions(state: ReaderErrorUiState): List<SourceErrorAction> {
    return buildList {
        add(SourceErrorAction.Retry)
        if (state.showOpenInWebView) add(SourceErrorAction.OpenInWebView)
        add(SourceErrorAction.Help)
        if (state.showMigrate) add(SourceErrorAction.Migrate)
    }
}

@Composable
internal fun ReaderErrorSurface(
    state: ReaderErrorUiState,
    actions: ReaderErrorUiActions,
) {
    val visibleActions = readerErrorActions(state)
    val headingFocusRequester = remember { FocusRequester() }
    val actionFocusRequesters = remember(visibleActions) { visibleActions.map { FocusRequester() } }

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
                    next = actionFocusRequesters.first()
                }
                .focusable(),
        )
        visibleActions.forEachIndexed { index, action ->
            Button(
                onClick = actions.onClickFor(action),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .focusRequester(actionFocusRequesters[index])
                    .focusProperties {
                        previous = actionFocusRequesters.getOrNull(index - 1) ?: headingFocusRequester
                        actionFocusRequesters.getOrNull(index + 1)?.let { next = it }
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
                Text(stringResource(action.labelRes))
            }
        }
        Text(
            text = stringResource(MR.strings.source_error_guidance),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

private fun ReaderErrorUiActions.onClickFor(action: SourceErrorAction): () -> Unit {
    return when (action) {
        SourceErrorAction.Retry -> onRetry
        SourceErrorAction.OpenInWebView -> onOpenInWebView
        SourceErrorAction.Help -> onHelp
        SourceErrorAction.Migrate -> onMigrate
    }
}
