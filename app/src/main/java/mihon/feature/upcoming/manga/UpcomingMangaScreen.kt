package mihon.feature.upcoming.manga

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.updates.UpdatesCategoryFilterDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

class UpcomingMangaScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel { UpcomingMangaScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()

        val hasActiveFilters = screenModel.includedCategories.isNotEmpty() ||
            screenModel.excludedCategories.isNotEmpty()

        UpcomingMangaScreenContent(
            state = state,
            hasActiveFilters = hasActiveFilters,
            setSelectedYearMonth = screenModel::setSelectedYearMonth,
            onClickUpcoming = { navigator.push(MangaScreen(it.id)) },
            onShowFilterDialog = { screenModel.setDialog(UpcomingMangaScreenModel.Dialog.Filter) },
        )

        when (val dialog = state.dialog) {
            UpcomingMangaScreenModel.Dialog.Filter -> {
                UpdatesCategoryFilterDialog(
                    categories = screenModel.categories.collectAsStateWithLifecycle().value,
                    included = screenModel.includedCategories,
                    excluded = screenModel.excludedCategories,
                    detailsText = stringResource(MR.strings.pref_filter_upcoming_categories_details),
                    onCycleCategory = screenModel::cycleCategory,
                    onDismissRequest = { screenModel.setDialog(null) },
                )
            }
            null -> {}
        }
    }
}
