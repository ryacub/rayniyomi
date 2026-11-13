package eu.kanade.tachiyomi.ui.recovery

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import eu.kanade.presentation.recovery.RecoveryActions
import eu.kanade.presentation.recovery.RecoveryScreenContent
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaSearchScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import kotlinx.coroutines.flow.map

class RecoveryScreen(
    private val media: RecoveryMedia,
    private val sourceId: Long? = null,
    private val sourceName: String? = null,
    private val originalIds: List<Long> = emptyList(),
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = assistedMetroViewModel<RecoveryScreenModel, RecoveryScreenModel.Factory> {
            create(media, sourceId, originalIds)
        }
        val state by screenModel.state.collectAsStateWithLifecycle()

        val activity = LocalActivity.current
        val hingeBounds = if (activity != null) {
            val hinges = remember(activity) {
                WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).map { layout ->
                    layout.displayFeatures.filterIsInstance<FoldingFeature>()
                        .firstOrNull { it.isSeparating && it.orientation == FoldingFeature.Orientation.VERTICAL }
                        ?.bounds
                }
            }
            hinges.collectAsStateWithLifecycle(initialValue = null).value
        } else {
            null
        }

        RecoveryScreenContent(
            state = state,
            media = media,
            hingeBounds = hingeBounds,
            failedSourceName = sourceName?.takeIf(String::isNotBlank),
            actions = RecoveryActions(
                navigateUp = navigator::pop,
                toggleOriginal = screenModel::toggleOriginal,
                toggleSource = screenModel::toggleSource,
                setKeepOriginal = screenModel::setKeepOriginal,
                findReplacements = screenModel::findReplacements,
                choose = screenModel::choose,
                approve = screenModel::approve,
                skip = screenModel::skip,
                reopen = screenModel::reopen,
                searchAgain = screenModel::searchAgain,
                searchRemaining = screenModel::searchRemaining,
                apply = screenModel::applySelected,
                cancel = screenModel::cancel,
                retryFailed = screenModel::retryFailed,
                reviewUnresolved = screenModel::reviewUnresolved,
                startAnother = screenModel::startAnother,
                openTitle = { id ->
                    navigator.push(if (media == RecoveryMedia.MANGA) MangaScreen(id) else AnimeScreen(id))
                },
                migrateAlone = { id ->
                    navigator.push(
                        if (media ==
                            RecoveryMedia.MANGA
                        ) {
                            MigrateMangaSearchScreen(id)
                        } else {
                            MigrateAnimeSearchScreen(id)
                        },
                    )
                },
            ),
        )
    }
}
