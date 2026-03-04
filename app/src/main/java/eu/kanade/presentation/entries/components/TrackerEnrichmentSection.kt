package eu.kanade.presentation.entries.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.kanade.domain.track.enrichment.model.EnrichedEntry
import eu.kanade.domain.track.enrichment.model.RecommendationChoice

@Composable
fun TrackerEnrichmentSection(
    state: EnrichedEntry?,
    loading: Boolean,
    refreshing: Boolean,
    errorText: String?,
    onRefresh: () -> Unit,
    onOpenRecommendation: (title: String, url: String) -> Unit,
) {
    var chooserOptions by remember { mutableStateOf<List<RecommendationChoice>>(emptyList()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Recommendations & Related",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                OutlinedButton(onClick = onRefresh, enabled = !refreshing) {
                    Text(if (refreshing) "Syncing…" else "Refresh recommendations")
                }
            }

            when {
                loading && state == null -> Text("Loading recommendations…")
                state == null -> Text("No recommendation data available yet")
                state.recommendations.isEmpty() && errorText.isNullOrBlank() -> Text("No recommendations found")
                else -> {
                    state.compositeScore?.let {
                        Text("Composite score: ${"%.2f".format(it)} (${state.confidenceLabel})")
                    }
                    if (state.sourceCoverage.isNotEmpty()) {
                        Text("Sources: ${state.sourceCoverage.joinToString(", ")}")
                    }
                    if (!errorText.isNullOrBlank()) {
                        Text(
                            text = "Some trackers failed: $errorText",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.recommendations.take(8), key = { it.stableKey }) { recommendation ->
                            val subtitle = buildString {
                                append(recommendation.trackerSources.joinToString(", "))
                                if (recommendation.inLibrary) {
                                    append(" • In library")
                                }
                                if (recommendation.confidence < 0.6) {
                                    append(" • low confidence")
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled =
                                        recommendation.targetUrl != null || recommendation.alternatives.isNotEmpty(),
                                    ) {
                                        when {
                                            recommendation.targetUrl != null && recommendation.confidence >= 0.6 -> {
                                                onOpenRecommendation(recommendation.title, recommendation.targetUrl)
                                            }
                                            recommendation.alternatives.isNotEmpty() -> {
                                                chooserOptions = recommendation.alternatives
                                            }
                                            else -> {
                                                recommendation.targetUrl?.let {
                                                    onOpenRecommendation(recommendation.title, it)
                                                }
                                            }
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                            ) {
                                Text(text = recommendation.title, style = MaterialTheme.typography.bodyLarge)
                                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
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
                            modifier = Modifier.clickable {
                                chooserOptions = emptyList()
                                option.targetUrl?.let { onOpenRecommendation(option.title, it) }
                            },
                        )
                    }
                }
            },
        )
    }
}
