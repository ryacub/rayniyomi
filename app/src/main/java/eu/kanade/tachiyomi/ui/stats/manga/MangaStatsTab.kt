package eu.kanade.tachiyomi.ui.stats.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.stats.MangaStatsScreenContent
import eu.kanade.presentation.more.stats.StatsScreenState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen

@Composable
fun Screen.mangaStatsTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow

    val screenModel = rememberScreenModel { MangaStatsScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()

    if (state is StatsScreenState.Loading) {
        LoadingScreen()
    }

    return TabContent(
        titleRes = AYMR.strings.label_manga,
        content = { contentPadding, _ ->

            if (state is StatsScreenState.Loading) {
                LoadingScreen()
            } else {
                MangaStatsScreenContent(
                    state = state as StatsScreenState.SuccessManga,
                    paddingValues = contentPadding,
                )
            }
        },
        navigateUp = navigator::pop,
    )
}
