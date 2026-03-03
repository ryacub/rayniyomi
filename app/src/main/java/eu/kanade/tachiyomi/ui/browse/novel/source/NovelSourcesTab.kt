package eu.kanade.tachiyomi.ui.browse.novel.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginLauncher
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginStateManager
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginUiState
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.novelSourcesTab(): TabContent {
    val pluginLauncher = remember { Injekt.get<LightNovelPluginLauncher>() }
    val stateManager = remember { Injekt.get<LightNovelPluginStateManager>() }
    val pluginUiState by stateManager.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val launchFailedMessage = stringResource(AYMR.strings.light_novel_browse_launch_failed)

    return TabContent(
        titleRes = AYMR.strings.label_novel_sources,
        content = { contentPadding, snackbarHostState ->
            NovelSourcesContent(
                contentPadding = contentPadding,
                pluginUiState = pluginUiState,
                onLaunchLibrary = {
                    scope.launch {
                        if (!pluginLauncher.launchLibrary()) {
                            snackbarHostState.showSnackbar(launchFailedMessage)
                        }
                    }
                },
            )
        },
    )
}

@Composable
private fun NovelSourcesContent(
    contentPadding: PaddingValues,
    pluginUiState: LightNovelPluginUiState,
    onLaunchLibrary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(AYMR.strings.label_novel_sources),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )

        Text(
            text = if (pluginUiState is LightNovelPluginUiState.Ready) {
                stringResource(AYMR.strings.light_novel_browse_ready_message)
            } else {
                stringResource(AYMR.strings.light_novel_browse_unavailable_message)
            },
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )

        if (pluginUiState is LightNovelPluginUiState.Ready) {
            Button(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .heightIn(min = 48.dp),
                onClick = onLaunchLibrary,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Launch,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(AYMR.strings.light_novel_open_library),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
