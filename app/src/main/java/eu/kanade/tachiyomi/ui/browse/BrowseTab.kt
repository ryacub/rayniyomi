package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginStateManager
import eu.kanade.tachiyomi.feature.novel.LightNovelPluginUiState
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.migration.sources.migrateAnimeSourceTab
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.mangaExtensionsTab
import eu.kanade.tachiyomi.ui.browse.manga.migration.sources.migrateMangaSourceTab
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.mangaSourcesTab
import eu.kanade.tachiyomi.ui.browse.novel.source.novelSourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.atomic.AtomicReference

data object BrowseTab : Tab {

    private val reselectTargetResolver = BrowseReselectTargetResolver()

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current is BrowseTab
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        when (reselectTargetResolver.resolvedTarget()) {
            BrowseSearchTarget.ANIME -> navigator.push(GlobalAnimeSearchScreen())
            BrowseSearchTarget.MANGA -> navigator.push(GlobalMangaSearchScreen())
            BrowseSearchTarget.UNKNOWN -> navigator.push(GlobalAnimeSearchScreen())
        }
    }

    private val switchToTabNumberChannel = Channel<Int>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToTabNumberChannel.trySend(3) // Manga extensions: tab no. 3
    }

    fun showAnimeExtension() {
        switchToTabNumberChannel.trySend(2) // Anime extensions: tab no. 2
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current

        // Hoisted for extensions tab's search bar
        val mangaExtensionsScreenModel = rememberScreenModel { MangaExtensionsScreenModel() }
        val mangaExtensionsState by mangaExtensionsScreenModel.state.collectAsStateWithLifecycle()

        val animeExtensionsScreenModel = rememberScreenModel { AnimeExtensionsScreenModel() }
        val animeExtensionsState by animeExtensionsScreenModel.state.collectAsStateWithLifecycle()
        val lightNovelPluginStateManager = Injekt.get<LightNovelPluginStateManager>()
        val lightNovelUiState by lightNovelPluginStateManager.uiState.collectAsStateWithLifecycle()

        val tabs = buildList {
            add(animeSourcesTab())
            add(mangaSourcesTab())
            add(animeExtensionsTab(animeExtensionsScreenModel))
            add(mangaExtensionsTab(mangaExtensionsScreenModel))
            add(migrateAnimeSourceTab())
            add(migrateMangaSourceTab())
            if (shouldShowNovelSourcesTab(lightNovelUiState)) {
                add(novelSourcesTab())
            }
        }.toPersistentList()

        val state = rememberPagerState { tabs.size }

        TabbedScreen(
            titleRes = MR.strings.browse,
            tabs = tabs,
            state = state,
            mangaSearchQuery = mangaExtensionsState.searchQuery,
            onChangeMangaSearchQuery = mangaExtensionsScreenModel::search,
            animeSearchQuery = animeExtensionsState.searchQuery,
            onChangeAnimeSearchQuery = animeExtensionsScreenModel::search,
            scrollable = true,
        )
        LaunchedEffect(tabs.size) {
            val maxIndex = tabs.lastIndex
            if (maxIndex >= 0 && state.currentPage > maxIndex) {
                state.scrollToPage(maxIndex)
            }
        }

        LaunchedEffect(tabs.size) {
            switchToTabNumberChannel.receiveAsFlow()
                .collectLatest { page ->
                    val maxIndex = tabs.lastIndex.coerceAtLeast(0)
                    state.scrollToPage(page.coerceIn(0, maxIndex))
                }
        }

        LaunchedEffect(state) {
            snapshotFlow { state.currentPage }
                .collectLatest { page ->
                    reselectTargetResolver.updateForPage(page)
                }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

internal fun shouldShowNovelSourcesTab(uiState: LightNovelPluginUiState): Boolean {
    return uiState is LightNovelPluginUiState.Ready
}

internal enum class BrowseSearchTarget {
    ANIME,
    MANGA,
    UNKNOWN,
}

internal class BrowseReselectTargetResolver(
    initialSearchTarget: BrowseSearchTarget = BrowseSearchTarget.UNKNOWN,
) {
    private val lastKnownSearchTarget = AtomicReference(initialSearchTarget)

    fun updateForPage(page: Int) {
        lastKnownSearchTarget.updateAndGet { current ->
            updateBrowseSearchTarget(current, page)
        }
    }

    fun resolvedTarget(): BrowseSearchTarget {
        return resolveBrowseReselectTarget(lastKnownSearchTarget.get())
    }
}

private const val ANIME_SOURCES_PAGE = 0
private const val MANGA_SOURCES_PAGE = 1
private const val ANIME_EXTENSIONS_PAGE = 2
private const val MANGA_EXTENSIONS_PAGE = 3
private const val ANIME_MIGRATION_PAGE = 4
private const val MANGA_MIGRATION_PAGE = 5

internal fun browseSearchTargetForPage(page: Int): BrowseSearchTarget {
    return when (page) {
        ANIME_SOURCES_PAGE, ANIME_EXTENSIONS_PAGE, ANIME_MIGRATION_PAGE -> BrowseSearchTarget.ANIME
        MANGA_SOURCES_PAGE, MANGA_EXTENSIONS_PAGE, MANGA_MIGRATION_PAGE -> BrowseSearchTarget.MANGA
        else -> BrowseSearchTarget.UNKNOWN
    }
}

internal fun updateBrowseSearchTarget(
    lastKnownSearchTarget: BrowseSearchTarget,
    page: Int,
): BrowseSearchTarget {
    return when (val pageTarget = browseSearchTargetForPage(page)) {
        BrowseSearchTarget.UNKNOWN -> lastKnownSearchTarget
        else -> pageTarget
    }
}

internal fun resolveBrowseReselectTarget(lastKnownSearchTarget: BrowseSearchTarget): BrowseSearchTarget {
    return when (lastKnownSearchTarget) {
        BrowseSearchTarget.UNKNOWN -> BrowseSearchTarget.ANIME
        else -> lastKnownSearchTarget
    }
}
