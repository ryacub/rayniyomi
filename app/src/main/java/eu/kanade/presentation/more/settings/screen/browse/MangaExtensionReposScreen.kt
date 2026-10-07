package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Composable
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.di.appGraph

class MangaExtensionReposScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = assistedMetroViewModel<ExtensionReposScreenModel, ExtensionReposScreenModel.Factory> {
            create(
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
