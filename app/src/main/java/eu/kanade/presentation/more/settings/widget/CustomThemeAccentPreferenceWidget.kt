package eu.kanade.presentation.more.settings.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import android.graphics.Color as AndroidColor

private const val OPAQUE_ALPHA_MASK = -0x1000000
private const val HUE_MAX = 360f
private const val SATURATION_MAX = 1f
private const val VALUE_MAX = 1f

internal val customAccentSwatches = listOf(
    0xFFE53935.toInt(), // Red
    0xFF8E24AA.toInt(), // Purple
    0xFF3949AB.toInt(), // Indigo
    0xFF1E88E5.toInt(), // Blue
    0xFF00897B.toInt(), // Teal
    0xFF43A047.toInt(), // Green
    0xFFFDD835.toInt(), // Yellow
    0xFFFB8C00.toInt(), // Orange
)

@Composable
internal fun CustomThemeAccentPreferenceWidget(
    selectedAccentSeed: Int,
    onSwatchClick: (Int) -> Unit,
    onOpenPicker: () -> Unit,
    onReset: () -> Unit,
) {
    BasePreferenceWidget(
        title = stringResource(MR.strings.pref_custom_theme_accent),
        subcomponent = {
            val selectedStateString = stringResource(MR.strings.selected)
            val notSelectedStateString = stringResource(MR.strings.not_selected)
            val chooseColorDescription = stringResource(MR.strings.pref_custom_theme_choose_color)
            val resetAccentDescription = stringResource(MR.strings.pref_custom_theme_reset_accent)
            Text(
                text = if (selectedAccentSeed == UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET) {
                    stringResource(MR.strings.pref_custom_theme_accent_using_default)
                } else {
                    stringResource(
                        MR.strings.pref_custom_theme_accent_using,
                        accentSeedToHex(selectedAccentSeed),
                    )
                },
                modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
                style = MaterialTheme.typography.bodyMedium,
            )
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PrefsHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(customAccentSwatches) { swatchSeed ->
                    val selected = normalizeAccentSeed(selectedAccentSeed) == normalizeAccentSeed(swatchSeed)
                    val swatchHex = accentSeedToHex(swatchSeed)
                    val swatchContentDescription = stringResource(
                        MR.strings.pref_custom_theme_accent_swatch_content_description,
                        swatchHex,
                    )
                    val swatchStateDescription = if (selected) selectedStateString else notSelectedStateString
                    FilterChip(
                        selected = selected,
                        onClick = { onSwatchClick(swatchSeed) },
                        label = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = swatchHex,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        },
                        leadingIcon = {
                            Row(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(
                                        color = androidx.compose.ui.graphics.Color(normalizeAccentSeed(swatchSeed)),
                                        shape = CircleShape,
                                    ),
                            ) {}
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .semantics {
                                contentDescription = swatchContentDescription
                                stateDescription = swatchStateDescription
                            },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PrefsHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onOpenPicker,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .semantics {
                            contentDescription = chooseColorDescription
                        },
                ) {
                    Text(text = stringResource(MR.strings.pref_custom_theme_choose_color))
                }
                TextButton(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .semantics {
                            contentDescription = resetAccentDescription
                        },
                ) {
                    Text(text = stringResource(MR.strings.pref_custom_theme_reset_accent))
                }
            }
        },
    )
}

@Composable
internal fun CustomThemeColorPickerDialog(
    sessionKey: Int,
    initialSeed: Int,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit,
) {
    val initialHsv = seedToHsv(initialSeed)
    var hue by rememberSaveable(sessionKey, initialSeed) { mutableFloatStateOf(initialHsv.hue) }
    var saturation by rememberSaveable(sessionKey, initialSeed) { mutableFloatStateOf(initialHsv.saturation) }
    var value by rememberSaveable(sessionKey, initialSeed) { mutableFloatStateOf(initialHsv.value) }

    val previewSeed = hsvToAccentSeed(hue, saturation, value)
    val previewHex = accentSeedToHex(previewSeed)
    val previewDescription = stringResource(
        MR.strings.pref_custom_theme_picker_preview_content_description,
        previewHex,
    )
    val hueDescription = stringResource(MR.strings.pref_custom_theme_picker_hue)
    val saturationDescription = stringResource(MR.strings.pref_custom_theme_picker_saturation)
    val valueDescription = stringResource(MR.strings.pref_custom_theme_picker_value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(MR.strings.pref_custom_theme_picker_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(MR.strings.pref_custom_theme_accent_using, previewHex),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color(previewSeed),
                            shape = MaterialTheme.shapes.medium,
                        )
                        .semantics {
                            contentDescription = previewDescription
                        },
                ) {}

                Text(text = stringResource(MR.strings.pref_custom_theme_picker_hue))
                Slider(
                    value = hue,
                    onValueChange = { hue = it.coerceIn(0f, HUE_MAX) },
                    valueRange = 0f..HUE_MAX,
                    modifier = Modifier.semantics {
                        contentDescription = hueDescription
                    },
                )

                Text(text = stringResource(MR.strings.pref_custom_theme_picker_saturation))
                Slider(
                    value = saturation,
                    onValueChange = { saturation = it.coerceIn(0f, SATURATION_MAX) },
                    valueRange = 0f..SATURATION_MAX,
                    modifier = Modifier.semantics {
                        contentDescription = saturationDescription
                    },
                )

                Text(text = stringResource(MR.strings.pref_custom_theme_picker_value))
                Slider(
                    value = value,
                    onValueChange = { value = it.coerceIn(0f, VALUE_MAX) },
                    valueRange = 0f..VALUE_MAX,
                    modifier = Modifier.semantics {
                        contentDescription = valueDescription
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(previewSeed)
                    onDismiss()
                },
            ) {
                Text(text = stringResource(MR.strings.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
    )
}

internal fun normalizeAccentSeed(seed: Int): Int = seed or OPAQUE_ALPHA_MASK

internal fun resolvePickerResultSeed(
    confirmSelection: Boolean,
    currentSeed: Int,
    draftSeed: Int,
): Int {
    return if (confirmSelection) normalizeAccentSeed(draftSeed) else currentSeed
}

internal data class AccentHsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
)

internal fun seedToHsv(seed: Int): AccentHsv {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(normalizeAccentSeed(seed), hsv)
    return AccentHsv(
        hue = hsv[0].coerceIn(0f, HUE_MAX),
        saturation = hsv[1].coerceIn(0f, SATURATION_MAX),
        value = hsv[2].coerceIn(0f, VALUE_MAX),
    )
}

internal fun hsvToAccentSeed(
    hue: Float,
    saturation: Float,
    value: Float,
): Int {
    return AndroidColor.HSVToColor(
        floatArrayOf(
            hue.coerceIn(0f, HUE_MAX),
            saturation.coerceIn(0f, SATURATION_MAX),
            value.coerceIn(0f, VALUE_MAX),
        ),
    ).let(::normalizeAccentSeed)
}

internal fun accentSeedToHex(seed: Int): String {
    val rgb = normalizeAccentSeed(seed) and 0x00FFFFFF
    return "#%06X".format(rgb)
}
