package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.di.appGraph

class MangaExtensionReposScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = viewModel {
            ExtensionReposScreenModel(
                MangaExtensionRepoDependencies(
                    getExtensionRepo = appGraph.getMangaExtensionRepo,
                    createExtensionRepo = appGraph.createMangaExtensionRepo,
                    deleteExtensionRepo = appGraph.deleteMangaExtensionRepo,
                    replaceExtensionRepo = appGraph.replaceMangaExtensionRepo,
                    updateExtensionRepo = appGraph.updateMangaExtensionRepo,
                ),
            )
        }
        ExtensionReposScreenContent(
            url = url,
            screenModel = screenModel,
        )
    }
}
