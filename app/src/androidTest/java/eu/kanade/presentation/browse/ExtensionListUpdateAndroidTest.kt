package eu.kanade.presentation.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.DpRect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cafe.adriel.voyager.navigator.Navigator
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.browse.anime.AnimeExtensionScreen
import eu.kanade.presentation.browse.manga.MangaExtensionScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsScreenModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ExtensionListUpdateAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun animeExtensionMovesToInstalledWithoutOverlappingItsNeighbours() {
        val updating = animeExtension("AnimeToki", "test.anime.toki", hasUpdate = true)
        val other = animeExtension("AnimeKai", "test.anime.kai", hasUpdate = false)
        var state by mutableStateOf(
            animeState(
                MR.strings.ext_updates_pending to listOf(updating to InstallStep.Installing),
                MR.strings.ext_installed to listOf(other to InstallStep.Idle),
            ),
        )
        composeRule.setContent {
            ScreenHost {
                AnimeExtensionScreen(
                    state = state,
                    contentPadding = PaddingValues(),
                    searchQuery = null,
                    onLongClickItem = {},
                    onClickItemCancel = {},
                    onClickItemDismiss = {},
                    onOpenWebView = {},
                    onInstallExtension = {},
                    onUninstallExtension = {},
                    onUpdateExtension = {},
                    onRetryExtension = {},
                    onTrustExtension = {},
                    onOpenExtension = {},
                    onClickUpdateAll = {},
                    onRefresh = {},
                )
            }
        }

        assertRowNeverOverlaps(row = "AnimeToki", otherRow = "AnimeKai") {
            state = animeState(
                MR.strings.ext_installed to listOf(
                    other to InstallStep.Idle,
                    updating.copy(versionCode = 3, hasUpdate = false) to InstallStep.Installing,
                ),
            )
        }
    }

    @Test
    fun mangaExtensionMovesToInstalledWithoutOverlappingItsNeighbours() {
        val updating = mangaExtension("MangaToki", "test.manga.toki", hasUpdate = true)
        val other = mangaExtension("MangaKai", "test.manga.kai", hasUpdate = false)
        var state by mutableStateOf(
            mangaState(
                MR.strings.ext_updates_pending to listOf(updating to InstallStep.Installing),
                MR.strings.ext_installed to listOf(other to InstallStep.Idle),
            ),
        )
        composeRule.setContent {
            ScreenHost {
                MangaExtensionScreen(
                    state = state,
                    contentPadding = PaddingValues(),
                    searchQuery = null,
                    onLongClickItem = {},
                    onClickItemCancel = {},
                    onClickItemDismiss = {},
                    onOpenWebView = {},
                    onInstallExtension = {},
                    onUninstallExtension = {},
                    onUpdateExtension = {},
                    onRetryExtension = {},
                    onTrustExtension = {},
                    onOpenExtension = {},
                    onClickUpdateAll = {},
                    onRefresh = {},
                )
            }
        }

        assertRowNeverOverlaps(row = "MangaToki", otherRow = "MangaKai") {
            state = mangaState(
                MR.strings.ext_installed to listOf(
                    other to InstallStep.Idle,
                    updating.copy(versionCode = 3, hasUpdate = false) to InstallStep.Installing,
                ),
            )
        }
    }

    private fun assertRowNeverOverlaps(row: String, otherRow: String, completeUpdate: () -> Unit) {
        val installedHeader = InstrumentationRegistry.getInstrumentation().targetContext
            .stringResource(MR.strings.ext_installed)
        composeRule.waitForIdle()
        val pendingTop = bounds(row).top

        composeRule.mainClock.autoAdvance = false
        completeUpdate()
        repeat(FRAMES_TO_CHECK) { frame ->
            composeRule.mainClock.advanceTimeByFrame()
            val rowBounds = bounds(row)
            listOf(installedHeader, otherRow).forEach { neighbour ->
                val neighbourBounds = bounds(neighbour)
                assertTrue(
                    "Frame $frame: $row at $rowBounds overlaps $neighbour at $neighbourBounds",
                    !overlapVertically(rowBounds, neighbourBounds),
                )
            }
        }

        assertTrue(
            "$row must move to the Installed section, not stay at $pendingTop",
            abs(bounds(row).top.value - pendingTop.value) > 1f,
        )
    }

    private fun bounds(text: String): DpRect = composeRule.onNodeWithText(text).getBoundsInRoot()

    private fun overlapVertically(first: DpRect, second: DpRect): Boolean =
        first.top.value < second.bottom.value - EDGE_TOLERANCE_DP &&
            second.top.value < first.bottom.value - EDGE_TOLERANCE_DP

    private fun animeExtension(name: String, pkgName: String, hasUpdate: Boolean) = AnimeExtension.Installed(
        name = name,
        pkgName = pkgName,
        versionName = "16.2",
        versionCode = 2,
        libVersion = 16.0,
        lang = "en",
        isNsfw = false,
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        hasUpdate = hasUpdate,
        isShared = true,
        signatureHash = "hash",
    )

    private fun mangaExtension(name: String, pkgName: String, hasUpdate: Boolean) = MangaExtension.Installed(
        name = name,
        pkgName = pkgName,
        versionName = "1.4.2",
        versionCode = 2,
        libVersion = 1.4,
        lang = "en",
        isNsfw = false,
        pkgFactory = null,
        sources = emptyList(),
        icon = null,
        hasUpdate = hasUpdate,
        isShared = true,
        signatureHash = "hash",
    )

    private fun animeState(
        vararg sections: Pair<StringResource, List<Pair<AnimeExtension, InstallStep>>>,
    ) = AnimeExtensionsScreenModel.State(
        isLoading = false,
        items = sections.associateTo(linkedMapOf()) { (header, items) ->
            AnimeExtensionUiModel.Header.Resource(header) to
                items.map { (extension, step) -> AnimeExtensionUiModel.Item(extension, step) }
        },
    )

    private fun mangaState(
        vararg sections: Pair<StringResource, List<Pair<MangaExtension, InstallStep>>>,
    ) = MangaExtensionsScreenModel.State(
        isLoading = false,
        items = sections.associateTo(linkedMapOf()) { (header, items) ->
            MangaExtensionUiModel.Header.Resource(header) to
                items.map { (extension, step) -> MangaExtensionUiModel.Item(extension, step) }
        },
    )

    @Composable
    private fun ScreenHost(content: @Composable () -> Unit) {
        MaterialTheme {
            Navigator(HostScreen) { content() }
        }
    }

    private object HostScreen : Screen() {
        @Composable
        override fun Content() {
            Text(text = "Host")
        }
    }

    private companion object {
        const val FRAMES_TO_CHECK = 60
        const val EDGE_TOLERANCE_DP = 0.5f
    }
}
