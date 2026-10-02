package eu.kanade.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import eu.kanade.tachiyomi.di.appGraph

@Composable
fun ifMangaSourcesLoaded(): Boolean {
    return remember { appGraph.mangaSourceManager.isInitialized }.collectAsState().value
}
