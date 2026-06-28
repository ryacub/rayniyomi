package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Composable
import cafe.adriel.voyager.core.model.rememberScreenModel
import eu.kanade.presentation.util.Screen
import mihon.domain.extensionrepo.manga.interactor.CreateMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.DeleteMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.ReplaceMangaExtensionRepo
import mihon.domain.extensionrepo.manga.interactor.UpdateMangaExtensionRepo
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MangaExtensionReposScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel {
            ExtensionReposScreenModel(
                MangaExtensionRepoDependencies(
                    getExtensionRepo = Injekt.get<GetMangaExtensionRepo>(),
                    createExtensionRepo = Injekt.get<CreateMangaExtensionRepo>(),
                    deleteExtensionRepo = Injekt.get<DeleteMangaExtensionRepo>(),
                    replaceExtensionRepo = Injekt.get<ReplaceMangaExtensionRepo>(),
                    updateExtensionRepo = Injekt.get<UpdateMangaExtensionRepo>(),
                ),
            )
        }
        ExtensionReposScreenContent(
            url = url,
            screenModel = screenModel,
        )
    }
}
