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

package eu.kanade.tachiyomi.ui.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.vivvvek.seeker.Segment
import eu.kanade.tachiyomi.ui.player.Sheets
import eu.kanade.tachiyomi.ui.player.controls.components.ControlsButton
import eu.kanade.tachiyomi.ui.player.controls.components.CurrentChapter
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun BottomLeftPlayerControls(
    playbackSpeed: Float,
    currentChapter: Segment?,
    onLockControls: () -> Unit,
    onCycleRotation: () -> Unit,
    orientationControlEnabled: Boolean,
    speedControlEnabled: Boolean,
    onPlaybackSpeedChange: (Float) -> Unit,
    onOpenSheet: (Sheets) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerPreferences = remember { Injekt.get<PlayerPreferences>() }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlsButton(
            Icons.Default.LockOpen,
            onClick = onLockControls,
        )
        if (orientationControlEnabled) {
            ControlsButton(
                icon = Icons.Default.ScreenRotation,
                onClick = onCycleRotation,
            )
        } else {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text(text = stringResource(MR.strings.rotation_not_available_large_screen))
                    }
                },
                state = rememberTooltipState(),
            ) {
                ControlsButton(
                    icon = Icons.Default.ScreenRotation,
                    onClick = onCycleRotation,
                    enabled = false,
                )
            }
        }
        if (speedControlEnabled) {
            ControlsButton(
                text = stringResource(AYMR.strings.player_speed, playbackSpeed),
                onClick = {
                    val newSpeed = if (playbackSpeed >= 2) 0.25f else playbackSpeed + 0.25f
                    onPlaybackSpeedChange(newSpeed)
                },
                onLongClick = { onOpenSheet(Sheets.PlaybackSpeed) },
            )
        } else {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = {
                    PlainTooltip {
                        Text(text = stringResource(AYMR.strings.player_speed_cast_unsupported))
                    }
                },
                state = rememberTooltipState(),
            ) {
                ControlsButton(
                    text = stringResource(AYMR.strings.player_speed, playbackSpeed),
                    onClick = {},
                    enabled = false,
                )
            }
        }
        AnimatedVisibility(
            currentChapter != null && playerPreferences.showCurrentChapter().get(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            CurrentChapter(
                chapter = currentChapter!!,
                onClick = { onOpenSheet(Sheets.Chapters) },
            )
        }
    }
}
