package eu.kanade.presentation.browse.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.extension.InstallStep
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionUiModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsScreenModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@RunWith(AndroidJUnit4::class)
class ExtensionTrustDialogAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overlappingDialogActionsAcceptOnlyTheFirstAction() {
        val selected = MangaExtension.Untrusted(
            name = "Trust callback fixture",
            pkgName = "eu.kanade.tachiyomi.extension.en.r1182fixture",
            versionName = "1.4.1",
            versionCode = 1,
            libVersion = 1.4,
            signatureHash = "r1182-fixture-signature",
        )
        val trusted = mutableListOf<MangaExtension.Untrusted>()
        val uninstalled = mutableListOf<MangaExtension>()
        var trustText = ""
        var uninstallText = ""
        val state = MangaExtensionsScreenModel.State(
            isLoading = false,
            items = mutableMapOf(
                MangaExtensionUiModel.Header.Text("Untrusted") to listOf(
                    MangaExtensionUiModel.Item(selected, InstallStep.Idle),
                ),
            ),
        )
        composeRule.setContent {
            MaterialTheme {
                trustText = stringResource(MR.strings.ext_trust)
                uninstallText = stringResource(MR.strings.ext_uninstall)
                Navigator(FixtureScreen) {
                    MangaExtensionScreen(
                        state = state,
                        contentPadding = PaddingValues(0.dp),
                        searchQuery = null,
                        onLongClickItem = {},
                        onClickItemCancel = {},
                        onClickItemDismiss = {},
                        onOpenWebView = {},
                        onInstallExtension = {},
                        onUninstallExtension = { uninstalled += it },
                        onUpdateExtension = {},
                        onRetryExtension = {},
                        onTrustExtension = { trusted += it },
                        onOpenExtension = {},
                        onClickUpdateAll = {},
                        onRefresh = {},
                    )
                }
            }
        }

        composeRule.onNodeWithText(selected.name).performClick()
        val dialog = composeRule.onNode(isDialog())
        val origin = dialog.fetchSemanticsNode().boundsInRoot.topLeft
        val trustPosition = composeRule.onNodeWithText(trustText).fetchSemanticsNode().boundsInRoot.center - origin
        val uninstallPosition =
            composeRule.onNodeWithText(uninstallText).fetchSemanticsNode().boundsInRoot.center - origin
        dialog.performTouchInput {
            down(0, trustPosition)
            down(1, uninstallPosition)
            advanceEventTime(50)
            up(1)
            advanceEventTime(1)
            up(0)
        }

        composeRule.runOnIdle {
            assertEquals(listOf(selected), uninstalled)
            assertEquals(emptyList<MangaExtension.Untrusted>(), trusted)
        }
        composeRule.onNodeWithText(trustText).assertDoesNotExist()
    }

    private object FixtureScreen : Screen() {
        @Composable
        override fun Content() = Unit
    }
}
