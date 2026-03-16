package eu.kanade.tachiyomi.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.domain.track.enrichment.model.DiscoverFeedItem
import eu.kanade.domain.track.enrichment.model.RecommendationChoice
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.util.LocalBackPress
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.openInBrowser
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.presentation.core.components.material.PullRefresh
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class DiscoverScreen : Screen() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val screenModel = rememberScreenModel { DiscoverScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val backPress = LocalBackPress.current
        var chooserOptions by remember { mutableStateOf<List<RecommendationChoice>>(emptyList()) }

        LaunchedEffect(Unit) {
            screenModel.announcements.collect { message ->
                screenModel.snackbarHostState.showSnackbar(message)
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = "Discover",
                    navigateUp = backPress,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = "Refresh Discover",
                                    icon = Icons.Outlined.Refresh,
                                    onClick = { screenModel.refresh(manual = true) },
                                ),
                            ),
                        )
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = screenModel.snackbarHostState) },
        ) { contentPadding ->
            PullRefresh(
                refreshing = state.refreshing,
                onRefresh = { screenModel.refresh(manual = true) },
                enabled = true,
                indicatorPadding = contentPadding,
            ) {
                when {
                    state.loading -> LoadingScreen(Modifier.padding(contentPadding))
                    state.items.isEmpty() -> EmptyScreen(
                        message = "No recommendations yet. Link trackers and refresh to build Discover.",
                        modifier = Modifier.padding(contentPadding),
                    )
                    else -> DiscoverFeed(
                        items = state.items,
                        errorText = state.errorText,
                        modifier = Modifier.padding(contentPadding),
                        onOpenRecommendation = { title, url ->
                            context.openInBrowser(url)
                        },
                        onChooseAlternative = { options ->
                            chooserOptions = options
                        },
                    )
                }
            }
        }

        if (chooserOptions.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { chooserOptions = emptyList() },
                confirmButton = {
                    TextButton(onClick = { chooserOptions = emptyList() }) {
                        Text("Close")
                    }
                },
                title = { Text("Choose recommendation source") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chooserOptions.forEach { option ->
                            Text(
                                text = "${option.title} (${option.trackerSource})",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        chooserOptions = emptyList()
                                        option.targetUrl?.let { context.openInBrowser(it) }
                                    }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun DiscoverFeed(
    items: List<DiscoverFeedItem>,
    errorText: String?,
    modifier: Modifier = Modifier,
    onOpenRecommendation: (title: String, url: String) -> Unit,
    onChooseAlternative: (List<RecommendationChoice>) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = "For You",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .semantics {
                        heading()
                        contentDescription = "Discover for you recommendations"
                    },
            )
        }

        if (!errorText.isNullOrBlank()) {
            item {
                Text(
                    text = "Some providers failed: $errorText",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .semantics {
                            stateDescription = "Partial recommendation results"
                        },
                )
            }
        }

        items(items, key = { "${it.mediaType}:${it.stableKey}" }) { item ->
            DiscoverCard(
                item = item,
                onOpenRecommendation = onOpenRecommendation,
                onChooseAlternative = onChooseAlternative,
            )
        }
    }
}

@Composable
private fun DiscoverCard(
    item: DiscoverFeedItem,
    onOpenRecommendation: (title: String, url: String) -> Unit,
    onChooseAlternative: (List<RecommendationChoice>) -> Unit,
) {
    val reason = item.reason.label
    val subtitle = buildString {
        append(item.trackerSources.joinToString(", "))
        append(" • ")
        append(reason)
        if (item.confidence < 0.6) {
            append(" • low confidence")
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(enabled = item.targetUrl != null || item.alternatives.isNotEmpty()) {
                when {
                    item.targetUrl != null && item.confidence >= 0.6 -> {
                        onOpenRecommendation(item.title, item.targetUrl)
                    }
                    item.alternatives.isNotEmpty() -> {
                        onChooseAlternative(item.alternatives)
                    }
                    else -> {
                        item.targetUrl?.let { onOpenRecommendation(item.title, it) }
                    }
                }
            }
            .semantics {
                contentDescription = "${item.title}. ${item.reason.label}. ${item.sourceCount} sources"
            },
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${"%.2f".format(item.score)}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
