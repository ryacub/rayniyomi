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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CastMiniController(
    episodeName: String,
    animeName: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = animeName,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        Text(
            text = episodeName,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )

        val positionText = formatCastTime(positionMs)
        val durationText = formatCastTime(durationMs)
        val sliderValue = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

        Slider(
            value = sliderValue,
            onValueChange = { newValue ->
                val newPosition = (newValue * durationMs).toLong()
                onSeek(newPosition)
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = "$positionText / $durationText"
                },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = positionText,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
            Text(
                text = durationText,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) {
                        stringResource(
                            MR.strings.action_pause,
                        )
                    } else {
                        stringResource(AYMR.strings.action_play)
                    },
                    tint = Color.White,
                )
            }
            IconButton(
                onClick = onDisconnect,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(AYMR.strings.cast_disconnect),
                    tint = Color.White,
                )
            }
        }
    }
}

internal fun formatCastTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${String.format("%02d", minutes)}:${String.format("%02d", secs)}"
    } else {
        "$minutes:${String.format("%02d", secs)}"
    }
}

@Preview
@Composable
private fun CastMiniControllerPreview() {
    CastMiniController(
        episodeName = "Episode 1 - The Beginning",
        animeName = "My Anime",
        isPlaying = true,
        positionMs = 65_000L,
        durationMs = 1_440_000L,
        onPlayPause = {},
        onSeek = {},
        onDisconnect = {},
    )
}
