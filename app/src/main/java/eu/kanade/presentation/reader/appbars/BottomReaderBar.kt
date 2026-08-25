package eu.kanade.presentation.reader.appbars

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.ArrowModifier
import eu.kanade.presentation.components.IndicatorModifier
import eu.kanade.presentation.components.IndicatorStrokeWidth
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.translation.TranslationState
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun BottomReaderBar(
    backgroundColor: Color,
    readingMode: ReadingMode,
    onClickReadingMode: () -> Unit,
    orientation: ReaderOrientation,
    onClickOrientation: () -> Unit,
    orientationControlEnabled: Boolean,
    cropEnabled: Boolean,
    onClickCropBorder: () -> Unit,
    hasTranslation: Boolean,
    translationState: TranslationState,
    translationEnabled: Boolean,
    onClickTranslation: () -> Unit,
    showWebtoonAutoScrollControls: Boolean,
    isAutoScrollRunning: Boolean,
    onToggleAutoScroll: () -> Unit,
    onToggleAutoScrollPanel: () -> Unit,
    onClickSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClickReadingMode) {
            Icon(
                painter = painterResource(readingMode.iconRes),
                contentDescription = stringResource(MR.strings.viewer),
            )
        }

        if (orientationControlEnabled) {
            IconButton(onClick = onClickOrientation) {
                Icon(
                    imageVector = orientation.icon,
                    contentDescription = stringResource(MR.strings.rotation_type),
                )
            }
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
                IconButton(
                    onClick = onClickOrientation,
                    enabled = false,
                ) {
                    Icon(
                        imageVector = orientation.icon,
                        contentDescription = stringResource(MR.strings.rotation_type),
                    )
                }
            }
        }

        IconButton(onClick = onClickCropBorder) {
            Icon(
                painter = painterResource(if (cropEnabled) R.drawable.ic_crop_24dp else R.drawable.ic_crop_off_24dp),
                contentDescription = stringResource(MR.strings.pref_crop_borders),
            )
        }

        if (translationState is TranslationState.Translating) {
            TranslatingReaderIndicator(
                currentPage = translationState.currentPage,
                totalPages = translationState.totalPages,
            )
        } else if (hasTranslation) {
            IconButton(onClick = onClickTranslation) {
                Icon(
                    imageVector = Icons.Outlined.Translate,
                    contentDescription = stringResource(AYMR.strings.pref_category_translation),
                    tint = if (translationEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        if (showWebtoonAutoScrollControls) {
            IconButton(onClick = onToggleAutoScroll) {
                Icon(
                    imageVector = if (isAutoScrollRunning) Icons.Outlined.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(
                        if (isAutoScrollRunning) {
                            MR.strings.action_pause_auto_scroll
                        } else {
                            MR.strings.action_start_auto_scroll
                        },
                    ),
                    tint = if (isAutoScrollRunning) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            IconButton(onClick = onToggleAutoScrollPanel) {
                Icon(
                    imageVector = Icons.Outlined.Speed,
                    contentDescription = stringResource(MR.strings.pref_webtoon_auto_scroll_speed),
                )
            }
        }

        IconButton(onClick = onClickSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(MR.strings.action_settings),
            )
        }
    }
}

/**
 * Mirrors the chapter-list TranslatingIndicator in ChapterTranslationIndicator.kt:
 * a determinate ring around the translate icon. Not clickable.
 */
@Composable
private fun TranslatingReaderIndicator(currentPage: Int, totalPages: Int) {
    Box(
        modifier = Modifier.size(IconButtonTokens.StateLayerSize),
        contentAlignment = Alignment.Center,
    ) {
        val progress = if (totalPages > 0) currentPage.toFloat() / totalPages else 0f
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            label = "reader_translation_progress",
        )
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = IndicatorModifier,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            strokeWidth = IndicatorStrokeWidth,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
            strokeCap = StrokeCap.Round,
        )
        Icon(
            imageVector = Icons.Outlined.Translate,
            contentDescription = null,
            modifier = ArrowModifier,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
