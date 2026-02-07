package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoConfirmDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoConflictDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoCreateDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionRepoDeleteDialog
import eu.kanade.presentation.more.settings.screen.browse.components.ExtensionReposScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.collectLatest
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionReposScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            ExtensionReposScreenModel(
                AnimeExtensionRepoDependencies(
                    getExtensionRepo = Injekt.get<GetAnimeExtensionRepo>(),
                    createExtensionRepo = Injekt.get<CreateAnimeExtensionRepo>(),
                    deleteExtensionRepo = Injekt.get<DeleteAnimeExtensionRepo>(),
                    replaceExtensionRepo = Injekt.get<ReplaceAnimeExtensionRepo>(),
                    updateExtensionRepo = Injekt.get<UpdateAnimeExtensionRepo>(),
                ),
            )
        }
        val state by screenModel.state.collectAsState()

        LaunchedEffect(url) {
            url?.let { screenModel.showDialog(RepoDialog.Confirm(it)) }
        }

        if (state is RepoScreenState.Loading) {
            LoadingScreen()
            return
        }

        val successState = state as RepoScreenState.Success

        ExtensionReposScreen(
            state = successState,
            onClickCreate = { screenModel.showDialog(RepoDialog.Create) },
            onOpenWebsite = { context.openInBrowser(it.website) },
            onClickDelete = { screenModel.showDialog(RepoDialog.Delete(it)) },
            onClickRefresh = { screenModel.refreshRepos() },
            navigateUp = navigator::pop,
        )

        when (val dialog = successState.dialog) {
            null -> {}
            is RepoDialog.Create -> {
                ExtensionRepoCreateDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.createRepo(it) },
                    repoUrls = successState.repos.map { it.baseUrl }.toImmutableSet(),
                )
            }
            is RepoDialog.Delete -> {
                ExtensionRepoDeleteDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onDelete = { screenModel.deleteRepo(dialog.repo) },
                    repo = dialog.repo,
                )
            }
            is RepoDialog.Conflict -> {
                ExtensionRepoConflictDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onMigrate = { screenModel.replaceRepo(dialog.newRepo) },
                    oldRepo = dialog.oldRepo,
                    newRepo = dialog.newRepo,
                )
            }
            is RepoDialog.Confirm -> {
                ExtensionRepoConfirmDialog(
                    onDismissRequest = screenModel::dismissDialog,
                    onCreate = { screenModel.createRepo(dialog.url) },
                    repo = dialog.url,
                )
            }
        }

        LaunchedEffect(Unit) {
            screenModel.events.collectLatest { event ->
                if (event is RepoEvent.LocalizedMessage) {
                    context.toast(event.stringRes)
                }
            }
        }
    }
}
