package xyz.rayniyomi.cloudstream.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.rayniyomi.cloudstream.R
import xyz.rayniyomi.cloudstream.data.InstalledPlugin
import xyz.rayniyomi.cloudstream.data.PluginEntry
import xyz.rayniyomi.cloudstream.data.PluginItem
import xyz.rayniyomi.cloudstream.data.PluginRows
import xyz.rayniyomi.cloudstream.data.Repository

private const val PRESET_NAME = "re-3arabi"
private const val PRESET_URL = "https://raw.githubusercontent.com/Abodabodd/re-3arabi/builds/repo.json"

data class ManagerActions(
    val giveConsent: () -> Unit = {},
    val addRepository: (String) -> Unit = {},
    val removeRepository: (String) -> Unit = {},
    val refresh: () -> Unit = {},
    val install: (String) -> Unit = {},
    val updateAll: () -> Unit = {},
    val setEnabled: (String, Boolean) -> Unit = { _, _ -> },
    val remove: (String) -> Unit = {},
    val dismissMessage: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerScreen(state: ManagerUiState, actions: ManagerActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        actions.dismissMessage()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manager_title)) },
                actions = {
                    if (state.consentGiven && state.repositories.isNotEmpty()) {
                        TextButton(onClick = actions.refresh, enabled = !state.loading) {
                            Text(stringResource(R.string.action_refresh))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.consentGiven) {
            ConsentContent(onAccept = actions.giveConsent, modifier = Modifier.padding(padding))
        } else {
            Column(Modifier.padding(padding)) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    repositorySection(state, actions)
                    pluginSection(state, actions)
                }
            }
        }
    }
}

@Composable
private fun ConsentContent(onAccept: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.consent_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.consent_body), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onAccept, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.consent_accept))
        }
    }
}

private fun LazyListScope.repositorySection(state: ManagerUiState, actions: ManagerActions) {
    item(key = "repositories-header") { SectionHeader(stringResource(R.string.section_repositories)) }
    item(key = "repository-input") {
        RepositoryInput(
            enabled = !state.loading,
            showPreset = state.repositories.none { it.url == PRESET_URL },
            onAdd = actions.addRepository,
        )
    }
    if (state.repositories.isEmpty()) {
        item(key = "no-repositories") {
            Text(stringResource(R.string.no_repositories), modifier = Modifier.padding(16.dp))
        }
    }
    items(state.repositories, key = { "repository:${it.url}" }) { repository ->
        RepositoryRow(repository, state.repositoryErrors[repository.url], actions.removeRepository)
    }
}

private fun LazyListScope.pluginSection(state: ManagerUiState, actions: ManagerActions) {
    if (state.repositories.isEmpty() && state.rows.items.isEmpty()) return
    item(key = "plugins-header") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SectionHeader(stringResource(R.string.section_plugins), Modifier.weight(1f))
            if (state.rows.updateCount > 0) {
                TextButton(onClick = actions.updateAll, modifier = Modifier.padding(end = 8.dp)) {
                    Text(stringResource(R.string.action_update_all, state.rows.updateCount))
                }
            }
        }
    }
    if (state.rows.hiddenCount > 0) {
        item(key = "hidden-count") {
            Text(
                stringResource(R.string.hidden_plugins, state.rows.hiddenCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
    if (state.rows.items.isEmpty() && !state.loading) {
        item(key = "no-plugins") {
            Text(stringResource(R.string.no_plugins), modifier = Modifier.padding(16.dp))
        }
    }
    items(state.rows.items, key = { "plugin:${it.internalName}" }) { item ->
        PluginRow(
            item = item,
            repositoryName = state.repositories.find { it.url == item.repositoryUrl }?.name ?: item.repositoryUrl,
            busy = item.internalName in state.busy,
            actions = actions,
        )
        HorizontalDivider()
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun RepositoryInput(enabled: Boolean, showPreset: Boolean, onAdd: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val submit = {
        onAdd(url)
        url = ""
    }
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text(stringResource(R.string.repository_url_hint)) },
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (url.isNotBlank()) submit() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = submit, enabled = enabled && url.isNotBlank()) {
                Text(stringResource(R.string.action_add))
            }
        }
        if (showPreset) {
            OutlinedButton(onClick = { onAdd(PRESET_URL) }, enabled = enabled) {
                Text(stringResource(R.string.action_add_preset, PRESET_NAME))
            }
        }
    }
}

@Composable
private fun RepositoryRow(repository: Repository, error: String?, onRemove: (String) -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(repository.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                repository.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error != null) {
                Text(
                    stringResource(R.string.repository_error, error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.action_remove)) }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.remove_repository_title)) },
            text = { Text(stringResource(R.string.remove_repository_body, repository.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onRemove(repository.url)
                    },
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun PluginRow(item: PluginItem, repositoryName: String, busy: Boolean, actions: ManagerActions) {
    val installed = item.installed
    val available = item.available
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    pluginDetails(item, repositoryName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (installed != null) {
                Switch(
                    checked = installed.enabled,
                    onCheckedChange = { actions.setEnabled(item.internalName, it) },
                    enabled = !busy,
                )
            }
        }
        Text(
            when {
                installed != null -> stringResource(
                    R.string.plugin_installed,
                    installed.version,
                    installed.fileSha256.take(SHORT_HASH_LENGTH),
                )
                available != null -> stringResource(R.string.plugin_version, available.version)
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (available == null) {
            Text(
                stringResource(R.string.plugin_not_listed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                if (installed == null && available != null) {
                    Button(onClick = { actions.install(item.internalName) }) {
                        Text(stringResource(R.string.action_install))
                    }
                }
                if (item.updateAvailable) {
                    Button(onClick = { actions.install(item.internalName) }) {
                        Text(stringResource(R.string.action_update))
                    }
                }
                if (installed != null) {
                    OutlinedButton(onClick = { actions.remove(item.internalName) }) {
                        Text(stringResource(R.string.action_remove))
                    }
                }
            }
        }
    }
}

private fun pluginDetails(item: PluginItem, repositoryName: String): String {
    val authors = item.available?.authors ?: item.installed?.authors.orEmpty()
    return listOfNotNull(
        repositoryName,
        item.internalName,
        authors.joinToString().takeIf { it.isNotEmpty() },
    ).joinToString(" · ")
}

private const val SHORT_HASH_LENGTH = 8

private val previewEntry = PluginEntry(
    url = "https://example.com/Animewitcher.cs3",
    version = 3,
    name = "Animewitcher",
    internalName = "Animewitcher",
    authors = listOf("Abodabodd"),
    tvTypes = listOf("Anime"),
    fileHash = "sha256-4c639f9392d46e45f4ee84e8799c810cc1d11fba2f13ff7e32865c71987c2fb4",
)

private val previewState = ManagerUiState(
    consentGiven = true,
    repositories = listOf(Repository(PRESET_URL, PRESET_NAME)),
    rows = PluginRows(
        items = listOf(
            PluginItem("Animewitcher", "Animewitcher", PRESET_URL, previewEntry, installed = null),
            PluginItem(
                "Bristege",
                "Bristege",
                PRESET_URL,
                previewEntry.copy(name = "Bristege", internalName = "Bristege", version = 2),
                InstalledPlugin(
                    internalName = "Bristege",
                    name = "Bristege",
                    version = 1,
                    language = "ar",
                    repositoryUrl = PRESET_URL,
                    fileSha256 = "0f1e2d3c4b5a69788796a5b4c3d2e1f0",
                    tvTypes = listOf("Anime"),
                    authors = listOf("Abodabodd"),
                ),
            ),
        ),
        hiddenCount = 28,
    ),
)

@Preview
@Composable
private fun ConsentPreview() {
    MaterialTheme { ManagerScreen(ManagerUiState(), ManagerActions()) }
}

@Preview
@Composable
private fun PluginsPreview() {
    MaterialTheme { ManagerScreen(previewState, ManagerActions()) }
}
