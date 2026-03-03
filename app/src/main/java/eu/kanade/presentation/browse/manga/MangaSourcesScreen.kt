package eu.kanade.presentation.browse.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.manga.components.BaseMangaSourceItem
import eu.kanade.tachiyomi.ui.browse.manga.source.MangaSourcesScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreenModel.Listing
import eu.kanade.tachiyomi.util.system.LocaleHelper
import tachiyomi.domain.source.manga.model.Pin
import tachiyomi.domain.source.manga.model.Source
import tachiyomi.domain.source.manga.model.SourceHealthStatus
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.ScrollbarLazyColumn
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.SECONDARY_ALPHA
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.components.material.topSmallPaddingValues
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.theme.header
import tachiyomi.presentation.core.util.plus
import tachiyomi.source.local.entries.manga.LocalMangaSource

@Composable
fun MangaSourcesScreen(
    state: MangaSourcesScreenModel.State,
    contentPadding: PaddingValues,
    onClickItem: (Source, Listing) -> Unit,
    onClickPin: (Source) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onRefresh: () -> Unit,
) {
    when {
        state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
        state.isEmpty -> EmptyScreen(
            stringRes = MR.strings.source_empty_screen,
            modifier = Modifier.padding(contentPadding),
        )
        else -> {
            PullRefresh(
                refreshing = state.isRefreshing,
                onRefresh = onRefresh,
                enabled = !state.isRefreshing,
                indicatorPadding = contentPadding,
            ) {
                ScrollbarLazyColumn(
                    contentPadding = contentPadding + topSmallPaddingValues,
                ) {
                    items(
                        items = state.items,
                        contentType = {
                            when (it) {
                                is MangaSourceUiModel.Header -> "header"
                                is MangaSourceUiModel.Item -> "item"
                            }
                        },
                        key = {
                            when (it) {
                                is MangaSourceUiModel.Header -> it.hashCode()
                                is MangaSourceUiModel.Item -> "source-${it.source.key()}"
                            }
                        },
                    ) { model ->
                        when (model) {
                            is MangaSourceUiModel.Header -> {
                                SourceHeader(
                                    modifier = Modifier.animateItem(),
                                    language = model.language,
                                )
                            }
                            is MangaSourceUiModel.Item -> SourceItem(
                                modifier = Modifier.animateItem(),
                                source = model.source,
                                onClickItem = onClickItem,
                                onLongClickItem = onLongClickItem,
                                onClickPin = onClickPin,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceHeader(
    language: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Text(
        text = LocaleHelper.getSourceDisplayName(language, context),
        modifier = modifier
            .padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
        style = MaterialTheme.typography.header,
    )
}

@Composable
private fun SourceItem(
    source: Source,
    onClickItem: (Source, Listing) -> Unit,
    onLongClickItem: (Source) -> Unit,
    onClickPin: (Source) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseMangaSourceItem(
        modifier = modifier,
        source = source,
        onClickItem = { onClickItem(source, Listing.Popular) },
        onLongClickItem = { onLongClickItem(source) },
        action = {
            if (source.supportsLatest) {
                TextButton(onClick = { onClickItem(source, Listing.Latest) }) {
                    Text(
                        text = stringResource(MR.strings.latest),
                        style = LocalTextStyle.current.copy(
                            color = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
            SourceHealthBadge(healthStatus = source.healthStatus)
            SourcePinButton(
                isPinned = Pin.Pinned in source.pin,
                onClick = { onClickPin(source) },
            )
        },
    )
}

@Composable
private fun SourceHealthBadge(
    healthStatus: SourceHealthStatus,
) {
    when (healthStatus) {
        SourceHealthStatus.HEALTHY -> {
            val desc = stringResource(AYMR.strings.source_health_healthy)
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .semantics { contentDescription = desc },
                tint = Color(0xFF4CAF50),
            )
        }
        SourceHealthStatus.DEGRADED -> {
            val desc = stringResource(AYMR.strings.source_health_degraded)
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .semantics { contentDescription = desc },
                tint = Color(0xFFFFC107),
            )
        }
        SourceHealthStatus.BROKEN -> {
            val desc = stringResource(AYMR.strings.source_health_broken)
            Icon(
                imageVector = Icons.Outlined.Error,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .semantics { contentDescription = desc },
                tint = Color(0xFFF44336),
            )
        }
        SourceHealthStatus.UNKNOWN -> {
            // No badge for unknown status
        }
    }
}

@Composable
private fun SourcePinButton(
    isPinned: Boolean,
    onClick: () -> Unit,
) {
    val icon = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
    val tint = if (isPinned) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onBackground.copy(
            alpha = SECONDARY_ALPHA,
        )
    }
    val description = if (isPinned) MR.strings.action_unpin else MR.strings.action_pin
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            tint = tint,
            contentDescription = stringResource(description),
        )
    }
}

@Composable
fun MangaSourceOptionsDialog(
    source: Source,
    onClickPin: () -> Unit,
    onClickDisable: () -> Unit,
    // SY -->
    onClickToggleDataSaver: (() -> Unit)?,
    // SY <--
    onClickRetryHealthCheck: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(text = source.visualName)
        },
        text = {
            Column {
                val textId = if (Pin.Pinned in source.pin) MR.strings.action_unpin else MR.strings.action_pin
                Text(
                    text = stringResource(textId),
                    modifier = Modifier
                        .clickable(onClick = onClickPin)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
                if (source.id != LocalMangaSource.ID) {
                    Text(
                        text = stringResource(MR.strings.action_disable),
                        modifier = Modifier
                            .clickable(onClick = onClickDisable)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY -->
                if (onClickToggleDataSaver != null) {
                    Text(
                        text = if (source.isExcludedFromDataSaver) {
                            stringResource(AYMR.strings.data_saver_stop_exclude)
                        } else {
                            stringResource(AYMR.strings.data_saver_exclude)
                        },
                        modifier = Modifier
                            .clickable(onClick = onClickToggleDataSaver)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
                // SY <--
                if (onClickRetryHealthCheck != null) {
                    Text(
                        text = stringResource(AYMR.strings.source_health_retry),
                        modifier = Modifier
                            .clickable(onClick = onClickRetryHealthCheck)
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
    )
}

sealed interface MangaSourceUiModel {
    data class Item(val source: Source) : MangaSourceUiModel
    data class Header(val language: String) : MangaSourceUiModel
}
