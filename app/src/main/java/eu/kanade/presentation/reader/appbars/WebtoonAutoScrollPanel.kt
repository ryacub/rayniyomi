package eu.kanade.presentation.reader.appbars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.BaseSliderItem
import tachiyomi.presentation.core.components.Pill
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

@Composable
fun WebtoonAutoScrollPanel(
    speedTenths: Int,
    onSelectPreset: (Int) -> Unit,
    onSpeedChange: (Int) -> Unit,
) {
    val clampedSpeedTenths = speedTenths.coerceIn(
        ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MIN,
        ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MAX,
    )
    val speedText = speedText(clampedSpeedTenths)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(bottom = 8.dp)
            .padding(MaterialTheme.padding.small)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(MR.strings.pref_webtoon_auto_scroll_speed),
                style = MaterialTheme.typography.bodyMedium,
            )
            Pill(
                text = speedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            listOf(10, 20, 30).forEach { preset ->
                FilterChip(
                    selected = clampedSpeedTenths == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(speedText(preset)) },
                )
            }
        }

        BaseSliderItem(
            value = clampedSpeedTenths,
            valueRange =
            ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MIN..ReaderPreferences.WEBTOON_AUTO_SCROLL_SPEED_MAX,
            label = stringResource(MR.strings.pref_webtoon_auto_scroll_speed),
            valueText = speedText,
            onChange = onSpeedChange,
            pillColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        )
    }
}

private fun speedText(value: Int): String {
    return String.format(Locale.US, "%.1fx", value / 10f)
}
