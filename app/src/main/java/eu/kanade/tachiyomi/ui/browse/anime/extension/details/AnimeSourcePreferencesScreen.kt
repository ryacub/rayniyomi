package eu.kanade.tachiyomi.ui.browse.anime.extension.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import eu.kanade.tachiyomi.ui.browse.sourceprefs.SourcePreferencesContent
import eu.kanade.tachiyomi.ui.browse.sourceprefs.buildSourcePreferenceScreen
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeSourcePreferencesScreen(val sourceId: Long) : Screen() {

    @Composable
    override fun Content() {
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val source = remember(sourceId) {
            Injekt.get<AnimeSourceManager>().getOrStub(sourceId)
        }

        val sourcePrefs = remember(sourceId, source) {
            if (source is ConfigurableAnimeSource) {
                source.sourcePreferences()
            } else {
                sourcePreferences("source_$sourceId")
            }
        }
        val preferenceScreen = remember(sourceId, sourcePrefs) {
            buildSourcePreferenceScreen(
                context = context,
                sourcePreferences = sourcePrefs,
            ) { screen ->
                if (source is ConfigurableAnimeSource) {
                    source.setupPreferenceScreen(screen)
                }
            }
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = source.toString(),
                    navigateUp = navigator::pop,
                    scrollBehavior = it,
                )
            },
        ) { contentPadding ->
            SourcePreferencesContent(
                preferenceScreen = preferenceScreen,
                sourcePreferences = sourcePrefs,
                contentPadding = contentPadding,
            )
        }
    }
}
