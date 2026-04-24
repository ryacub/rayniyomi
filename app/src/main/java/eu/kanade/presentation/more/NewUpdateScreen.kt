package eu.kanade.presentation.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun NewUpdateScreen(
    versionName: String,
    changelogInfo: String,
    releaseDateEpochMillis: Long? = null,
    isInstallAction: Boolean = false,
    onOpenInBrowser: () -> Unit,
    onRejectUpdate: () -> Unit,
    onAcceptUpdate: () -> Unit,
    onSkipVersion: (() -> Unit)? = null,
) {
    Scaffold(
        bottomBar = {
            val strokeWidth = Dp.Hairline
            val borderColor = MaterialTheme.colorScheme.outline
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .drawBehind {
                        drawLine(
                            borderColor,
                            Offset(0f, 0f),
                            Offset(size.width, 0f),
                            strokeWidth.value,
                        )
                    }
                    .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAcceptUpdate,
                ) {
                    Text(
                        text = stringResource(
                            if (isInstallAction) {
                                MR.strings.action_install
                            } else {
                                MR.strings.update_check_confirm
                            },
                        ),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (onSkipVersion != null) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = onSkipVersion,
                        ) {
                            Text(text = stringResource(MR.strings.update_check_skip_version))
                        }
                    }
                    TextButton(
                        modifier = Modifier.weight(1f),
                        onClick = onRejectUpdate,
                    ) {
                        Text(text = stringResource(MR.strings.action_not_now))
                    }
                }
            }
        },
    ) { paddingValues ->
        // Status bar scrim
        Box(
            modifier = Modifier
                .zIndex(2f)
                .secondaryItemAlpha()
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .height(paddingValues.calculateTopPadding()),
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(top = 48.dp)
                .padding(horizontal = MaterialTheme.padding.medium),
        ) {
            Icon(
                imageVector = Icons.Outlined.NewReleases,
                contentDescription = null,
                modifier = Modifier
                    .padding(bottom = MaterialTheme.padding.small)
                    .size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(MR.strings.update_check_notification_update_available),
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = versionName,
                modifier = Modifier
                    .secondaryItemAlpha()
                    .padding(vertical = MaterialTheme.padding.small),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(
                    MR.strings.update_check_release_channel,
                    stringResource(MR.strings.update_check_channel_stable),
                ),
                modifier = Modifier.secondaryItemAlpha(),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = stringResource(MR.strings.update_check_release_source, "ryacub/rayniyomi"),
                modifier = Modifier.secondaryItemAlpha(),
                style = MaterialTheme.typography.bodySmall,
            )
            releaseDateEpochMillis?.let { releaseDate ->
                val formattedDate = remember(releaseDate) {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(
                        LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(releaseDate),
                            ZoneId.systemDefault(),
                        ),
                    )
                }
                Text(
                    text = stringResource(MR.strings.update_check_release_date, formattedDate),
                    modifier = Modifier
                        .secondaryItemAlpha()
                        .padding(top = MaterialTheme.padding.extraSmall),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            RichText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaterialTheme.padding.large),
                style = RichTextStyle(
                    stringStyle = RichTextStringStyle(
                        linkStyle = SpanStyle(color = MaterialTheme.colorScheme.primary),
                    ),
                ),
            ) {
                Markdown(content = changelogInfo)

                TextButton(
                    onClick = onOpenInBrowser,
                    modifier = Modifier.padding(top = MaterialTheme.padding.small),
                ) {
                    Text(text = stringResource(MR.strings.update_check_open))
                    Spacer(modifier = Modifier.width(MaterialTheme.padding.extraSmall))
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                        contentDescription = stringResource(MR.strings.action_open_in_browser),
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun NewUpdateScreenPreview() {
    TachiyomiPreviewTheme {
        NewUpdateScreen(
            versionName = "v0.99.9",
            changelogInfo = """
                ## Yay
                Foobar

                ### More info
                - Hello
                - World
            """.trimIndent(),
            isInstallAction = false,
            onOpenInBrowser = {},
            onRejectUpdate = {},
            onAcceptUpdate = {},
            onSkipVersion = {},
        )
    }
}
