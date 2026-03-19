package eu.kanade.presentation.more.settings.widget

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.theme.TachiyomiTheme
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType

@Composable
internal fun AppThemePreferenceWidget(
    value: AppTheme,
    amoled: Boolean,
    customAccentSeed: Int,
    onItemClick: (AppTheme) -> Unit,
    onCustomAccentSeedChange: (Int) -> Unit,
) {
    BasePreferenceWidget(
        subcomponent = {
            AppThemesList(
                currentTheme = value,
                amoled = amoled,
                customAccentSeed = customAccentSeed,
                onItemClick = onItemClick,
                onCustomAccentSeedChange = onCustomAccentSeedChange,
            )
        },
    )
}

@Composable
private fun AppThemesList(
    currentTheme: AppTheme,
    amoled: Boolean,
    customAccentSeed: Int,
    onItemClick: (AppTheme) -> Unit,
    onCustomAccentSeedChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val appThemes = remember {
        availableAppThemes(DeviceUtil.isDynamicColorAvailable)
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = PrefsHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        items(
            items = appThemes,
            key = { it.name },
        ) { appTheme ->
            Column(
                modifier = Modifier
                    .width(114.dp)
                    .padding(top = 8.dp),
            ) {
                TachiyomiTheme(
                    appTheme = appTheme,
                    amoled = amoled,
                ) {
                    AppThemePreviewItem(
                        selected = currentTheme == appTheme,
                        onClick = {
                            onItemClick(appTheme)
                            (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        },
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(appTheme.titleRes!!),
                    modifier = Modifier
                        .fillMaxWidth()
                        .secondaryItemAlpha(),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (currentTheme == AppTheme.CUSTOM) {
        Text(
            text = stringResource(MR.strings.pref_custom_theme_accent),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PrefsHorizontalPadding, vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(MR.strings.pref_custom_theme_accent_summary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PrefsHorizontalPadding)
                .secondaryItemAlpha(),
            style = MaterialTheme.typography.bodyMedium,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = PrefsHorizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            items(
                items = customThemeAccentSeeds,
                key = { it },
            ) { seed ->
                CustomThemeAccentSwatch(
                    seed = seed,
                    selected = seed == customAccentSeed,
                    onClick = {
                        if (seed != customAccentSeed) {
                            onCustomAccentSeedChange(seed)
                            (context as? Activity)?.let { ActivityCompat.recreate(it) }
                        }
                    },
                )
            }
        }
        TextButton(
            onClick = {
                if (customAccentSeed != UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET) {
                    onCustomAccentSeedChange(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET)
                    (context as? Activity)?.let { ActivityCompat.recreate(it) }
                }
            },
            modifier = Modifier
                .padding(start = PrefsHorizontalPadding),
        ) {
            Text(text = stringResource(MR.strings.action_reset))
        }
    }
}

internal val customThemeAccentSeeds = listOf(
    0xFF4285F4.toInt(),
    0xFFEF6C00.toInt(),
    0xFF2E7D32.toInt(),
    0xFF8E24AA.toInt(),
    0xFFD81B60.toInt(),
    0xFF00695C.toInt(),
    0xFFF9A825.toInt(),
    0xFF455A64.toInt(),
)

@Composable
private fun CustomThemeAccentSwatch(
    seed: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(color = androidx.compose.ui.graphics.Color(seed))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else DividerDefaults.color,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = stringResource(MR.strings.selected),
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal fun availableAppThemes(isDynamicColorAvailable: Boolean): List<AppTheme> {
    return AppTheme.entries
        .filterNot { it.titleRes == null || (it == AppTheme.MONET && !isDynamicColorAvailable) }
}

@Composable
fun AppThemePreviewItem(
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .border(
                width = 4.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    DividerDefaults.color
                },
                shape = RoundedCornerShape(17.dp),
            )
            .padding(4.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick),
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .weight(0.7f)
                    .padding(end = 4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = MaterialTheme.shapes.small,
                    ),
            )

            Box(
                modifier = Modifier.weight(0.3f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(MR.strings.selected),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Cover
        Box(
            modifier = Modifier
                .padding(start = 8.dp, top = 2.dp)
                .background(
                    color = DividerDefaults.color,
                    shape = MaterialTheme.shapes.small,
                )
                .fillMaxWidth(0.5f)
                .aspectRatio(ItemCover.Book.ratio),
        ) {
            Row(
                modifier = Modifier
                    .padding(4.dp)
                    .size(width = 24.dp, height = 16.dp)
                    .clip(RoundedCornerShape(5.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(MaterialTheme.colorScheme.tertiary),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(12.dp)
                        .background(MaterialTheme.colorScheme.secondary),
                )
            }
        }

        // Bottom bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(17.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .alpha(0.6f)
                            .height(17.dp)
                            .weight(1f)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AppThemesListPreview() {
    var appTheme by remember { mutableStateOf(AppTheme.DEFAULT) }
    Injekt.addSingleton(fullType<UiPreferences>(), UiPreferences(InMemoryPreferenceStore()))
    TachiyomiTheme(appTheme = appTheme) {
        Surface {
            AppThemesList(
                currentTheme = appTheme,
                amoled = false,
                customAccentSeed = UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET,
                onItemClick = { appTheme = it },
                onCustomAccentSeedChange = { },
            )
        }
    }
}
