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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SubtitleSettingsDialog(
    fontSize: Float,
    onFontSizeChange: (Float) -> Unit,
    fgColor: Color,
    onFgColorChange: (Color) -> Unit,
    bgColor: Color,
    onBgColorChange: (Color) -> Unit,
    edgeType: Int,
    onEdgeTypeChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Subtitle Settings")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Font Size: ${String.format("%.1f", fontSize)}x",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Slider(
                        value = fontSize,
                        onValueChange = onFontSizeChange,
                        valueRange = 0.5f..2.0f,
                        steps = 7,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Foreground Color",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    ColorPickerRow(
                        selectedColor = fgColor,
                        onColorSelected = onFgColorChange,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Background Color",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    ColorPickerRow(
                        selectedColor = bgColor,
                        onColorSelected = onBgColorChange,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Edge Type",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0 to "None",
                            1 to "Outline",
                            2 to "Drop Shadow",
                        ).forEach { (type, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onEdgeTypeChange(type) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(
                                    selected = edgeType == type,
                                    onClick = { onEdgeTypeChange(type) },
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
    )
}

@Composable
private fun ColorPickerRow(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
) {
    val colors = listOf(
        Color.White,
        Color.Black,
        Color.Yellow,
        Color.Red,
        Color.Green,
        Color.Blue,
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
                    .clickable { onColorSelected(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (selectedColor == color) {
                    val textColor = if (color == Color.White || color == Color.Yellow) Color.Black else Color.White
                    Text(
                        text = "✓",
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
