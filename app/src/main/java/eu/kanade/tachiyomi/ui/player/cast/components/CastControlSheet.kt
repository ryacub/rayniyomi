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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.model.VideoTrack
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun CastControlSheet(
    subtitleTracks: List<VideoTrack>,
    selectedSubtitleIndex: Int,
    onSelectSubtitle: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val castCompatible = subtitleTracks.filter {
        it.name.endsWith(".srt", ignoreCase = true) ||
            it.name.endsWith(".vtt", ignoreCase = true)
    }

    val filteredCount = subtitleTracks.size - castCompatible.size

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(AYMR.strings.cast_subtitles),
            style = MaterialTheme.typography.titleMedium,
        )

        if (filteredCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = stringResource(AYMR.strings.cast_subtitle_unsupported_format),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(0f),
                )
                Text(
                    text = stringResource(AYMR.strings.cast_subtitle_unsupported_format),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(castCompatible) { index, track ->
                SubtitleTrackItem(
                    track = track,
                    isSelected = index == selectedSubtitleIndex,
                    onSelect = { onSelectSubtitle(index) },
                )
            }
        }
    }
}

@Composable
private fun SubtitleTrackItem(
    track: VideoTrack,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onSelect() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            modifier = Modifier.weight(0f),
        )
        Text(
            text = track.language ?: track.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview
@Composable
private fun CastControlSheetPreview() {
    CastControlSheet(
        subtitleTracks = listOf(
            VideoTrack(id = 0, name = "English.srt", language = "English"),
            VideoTrack(id = 1, name = "Japanese.vtt", language = "Japanese"),
            VideoTrack(id = 2, name = "Chinese.ass", language = "Chinese"),
        ),
        selectedSubtitleIndex = 0,
        onSelectSubtitle = {},
        onDismiss = {},
    )
}
