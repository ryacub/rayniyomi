package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.presentation.util.Screen
import mihon.domain.extensionrepo.anime.interactor.CreateAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.DeleteAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.ReplaceAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.interactor.UpdateAnimeExtensionRepo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeExtensionReposScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
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
        ExtensionReposScreenContent(
            url = url,
            screenModel = screenModel,
        )
    }
}
