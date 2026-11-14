package eu.kanade.tachiyomi.ui.entries

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.navigator.Navigator
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.core.util.ifMangaSourcesLoaded
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreenModel
import eu.kanade.tachiyomi.ui.history.manga.MangaHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.manga.mangaHistoryTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.entries.manga.LocalMangaSource

@RunWith(AndroidJUnit4::class)
class MangaDuplicateDialogAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun historyDuplicateSheetDoesNotOfferMerge() = assertMergeIsAbsent(HistoryScreen)

    @Test
    fun browseDuplicateSheetDoesNotOfferMerge() = assertMergeIsAbsent(BrowseScreen)

    private fun assertMergeIsAbsent(screen: Screen) {
        var addAnyway = ""
        var mergeEntries = ""
        composeRule.setContent {
            addAnyway = stringResource(MR.strings.action_add_anyway)
            mergeEntries = stringResource(AYMR.strings.action_merge_entries)
            MaterialTheme {
                CompositionLocalProvider(LocalMetroViewModelFactory provides appGraph.metroViewModelFactory) {
                    Navigator(screen)
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodes(hasText(addAnyway)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(mergeEntries).assertDoesNotExist()
    }

    private object HistoryScreen : Screen() {
        @Composable
        override fun Content() {
            val screenModel = metroViewModel<MangaHistoryScreenModel>()
            LaunchedEffect(screenModel) {
                screenModel.setDialog(
                    MangaHistoryScreenModel.Dialog.DuplicateManga(
                        manga = Manga.create().copy(id = 1, title = "Test title"),
                        duplicate = Manga.create().copy(id = 2, title = "Test title", favorite = true),
                    ),
                )
            }
            val tab = mangaHistoryTab(LocalContext.current, fromMore = false)
            tab.content(PaddingValues(0.dp), SnackbarHostState())
        }
    }

    private object BrowseScreen : Screen() {
        @Composable
        override fun Content() {
            if (!ifMangaSourcesLoaded()) return

            val screenModel =
                assistedMetroViewModel<BrowseMangaSourceScreenModel, BrowseMangaSourceScreenModel.Factory> {
                    create(sourceId = LocalMangaSource.ID, listingQuery = null)
                }
            LaunchedEffect(screenModel) {
                screenModel.setDialog(
                    BrowseMangaSourceScreenModel.Dialog.AddDuplicateManga(
                        manga = Manga.create().copy(id = 1, title = "Test title"),
                        duplicate = Manga.create().copy(id = 2, title = "Test title", favorite = true),
                    ),
                )
            }
            BrowseMangaSourceScreen(LocalMangaSource.ID, null).Content()
        }
    }
}
