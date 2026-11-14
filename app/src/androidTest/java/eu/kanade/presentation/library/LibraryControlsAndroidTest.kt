package eu.kanade.presentation.library

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.navigator.Navigator
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.metroViewModel
import eu.kanade.presentation.library.manga.MangaLibrarySettingsDialog
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR

@RunWith(AndroidJUnit4::class)
class LibraryControlsAndroidTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fullLibraryUpdateShowsLibrarySnackbar() {
        setContent { Navigator(MangaLibraryTab) { MangaLibraryTab.Content() } }
        with(composeRule.activity) {
            composeRule.onNodeWithContentDescription(stringResource(MR.strings.action_menu_overflow_description))
                .performSemanticsAction(SemanticsActions.OnClick)
            composeRule.onNodeWithText(stringResource(MR.strings.action_update_library)).performSemanticsAction(SemanticsActions.OnClick)
            val snackbarContent = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            val snackbarText = snackbarContent and SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodes(snackbarText).fetchSemanticsNodes().isNotEmpty()
            }
            val message = composeRule.onNode(snackbarText).fetchSemanticsNode()
                .config.getOrNull(SemanticsProperties.Text)?.single()?.text
            assertEquals(stringResource(MR.strings.updating_library), message)
        }
    }

    @Test
    fun listSizeSliderChangesRowSizePreferenceAndPreservesGridColumns() {
        val preferences = appGraph.libraryPreferences
        val columns = if (composeRule.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            preferences.mangaLandscapeColumns()
        } else {
            preferences.mangaPortraitColumns()
        }
        withPreference(preferences.displayMode(), LibraryDisplayMode.List) {
            withPreference(preferences.libraryListSize(), 4) {
                withPreference(columns, 3) {
                    setContent {
                        MangaLibrarySettingsDialog(
                            onDismissRequest = {},
                            screenModel = metroViewModel(),
                            category = null,
                        )
                    }
                    val displayTab = composeRule.activity.stringResource(MR.strings.action_display)
                    composeRule.onNodeWithText(displayTab).performSemanticsAction(SemanticsActions.OnClick)
                    composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
                        .performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
                    composeRule.runOnIdle {
                        assertEquals("The slider must write the shared list size", 7, preferences.libraryListSize().get())
                        assertEquals("The slider must preserve the grid columns", 3, columns.get())
                    }
                }
            }
        }
    }

    private fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalMetroViewModelFactory provides appGraph.metroViewModelFactory) {
                    content()
                }
            }
        }
    }

    private inline fun <T> withPreference(preference: Preference<T>, value: T, block: () -> Unit) {
        val previous = preference.get().takeIf { preference.isSet() }
        preference.set(value)
        try {
            block()
        } finally {
            if (previous == null) preference.delete() else preference.set(previous)
        }
    }
}
