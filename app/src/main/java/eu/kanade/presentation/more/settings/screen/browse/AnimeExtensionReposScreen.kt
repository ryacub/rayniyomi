package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Composable
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.di.appGraph

class AnimeExtensionReposScreen(
    private val url: String? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = assistedMetroViewModel<ExtensionReposScreenModel, ExtensionReposScreenModel.Factory> {
            create(
                AnimeExtensionRepoDependencies(
                    getExtensionRepo = appGraph.getAnimeExtensionRepo,
                    createExtensionRepo = appGraph.createAnimeExtensionRepo,
                    deleteExtensionRepo = appGraph.deleteAnimeExtensionRepo,
                    replaceExtensionRepo = appGraph.replaceAnimeExtensionRepo,
                    updateExtensionRepo = appGraph.updateAnimeExtensionRepo,
                ),
            )
        }
        ExtensionReposScreenContent(
            url = url,
            screenModel = screenModel,
        )
    }
}
