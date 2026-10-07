package eu.kanade.tachiyomi.ui.browse.manga.migration.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.browse.manga.MigrateMangaSearchScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.sourceWebViewScreen

class MigrateMangaSearchScreen(private val mangaId: Long) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel =
            assistedMetroViewModel<MigrateMangaSearchScreenModel, MigrateMangaSearchScreenModel.Factory> {
                create(mangaId = mangaId, initialExtensionFilter = "")
            }
        val state by screenModel.state.collectAsStateWithLifecycle()

        val dialogScreenModel = assistedMetroViewModel<
            MangaMigrateSearchScreenDialogScreenModel,
            MangaMigrateSearchScreenDialogScreenModel.Factory,
            > {
            create(mangaId = mangaId)
        }
        val dialogState by dialogScreenModel.state.collectAsStateWithLifecycle()

        MigrateMangaSearchScreen(
            state = state,
            fromSourceId = dialogState.manga?.source,
            navigateUp = navigator::pop,
            onChangeSearchQuery = screenModel::updateSearchQuery,
            onSearch = { screenModel.search() },
            getManga = { screenModel.getManga(it) },
            onChangeSearchFilter = screenModel::setSourceFilter,
            onToggleResults = screenModel::toggleFilterResults,
            onRetrySource = screenModel::retrySource,
            onWebViewSource = { source -> sourceWebViewScreen(source)?.let(navigator::push) },
            onClickSource = {
                navigator.push(
                    MangaSourceSearchScreen(dialogState.manga!!, it.id, state.searchQuery),
                )
            },
            onClickItem = {
                dialogScreenModel.setDialog(
                    MangaMigrateSearchScreenDialogScreenModel.Dialog.Migrate(it),
                )
            },
            onLongClickItem = { navigator.push(MangaScreen(it.id, true)) },
        )

        when (val dialog = dialogState.dialog) {
            is MangaMigrateSearchScreenDialogScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    oldManga = dialogState.manga!!,
                    newManga = dialog.manga,
                    screenModel = metroViewModel<MigrateMangaDialogScreenModel>(),
                    onDismissRequest = { dialogScreenModel.setDialog(null) },
                    onClickTitle = {
                        navigator.push(MangaScreen(dialog.manga.id, true))
                    },
                    onPopScreen = {
                        if (navigator.lastItem is MangaScreen) {
                            val lastItem = navigator.lastItem
                            navigator.popUntil { navigator.items.contains(lastItem) }
                            navigator.push(MangaScreen(dialog.manga.id))
                        } else {
                            navigator.replace(MangaScreen(dialog.manga.id))
                        }
                    },
                )
            }
            else -> {}
        }
    }
}
