/*
 * Copyright 2024 Abdallah Mehiz
 * https://github.com/abdallahmehiz/mpvKt
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.kanade.tachiyomi.ui.player.cast.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import eu.kanade.tachiyomi.ui.player.cast.CastState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CastButton(
    enabled: Boolean,
    castState: CastState,
    modifier: Modifier = Modifier,
) {
    val contentDesc = when (castState) {
        CastState.CONNECTED -> stringResource(AYMR.strings.cast_button_connected)
        CastState.CONNECTING -> stringResource(AYMR.strings.cast_button_connecting)
        CastState.DISCONNECTED -> stringResource(AYMR.strings.cast_button_idle)
    }

    AndroidView(
        factory = { ctx ->
            MediaRouteButton(ctx).apply {
                isEnabled = enabled
            }
        },
        update = { view ->
            view.isEnabled = enabled
        },
        modifier = modifier
            .size(48.dp)
            .semantics {
                this.contentDescription = contentDesc
            },
    )
}

@Preview
@Composable
private fun CastButtonConnectedPreview() {
    CastButton(enabled = true, castState = CastState.CONNECTED)
}

@Preview
@Composable
private fun CastButtonDisabledPreview() {
    CastButton(enabled = false, castState = CastState.DISCONNECTED)
}
