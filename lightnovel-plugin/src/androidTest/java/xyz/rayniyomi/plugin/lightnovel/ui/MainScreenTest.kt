package xyz.rayniyomi.plugin.lightnovel.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook

class MainScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateAndImportButtonAreVisible() {
        composeRule.setContent {
            MainScreen(
                books = emptyList(),
                statusMessage = "",
                isLoading = false,
                snackbarHostState = SnackbarHostState(),
                onImportClick = {},
                onBookClick = {},
            )
        }

        composeRule.onNodeWithTag(MainScreenTags.IMPORT_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(MainScreenTags.EMPTY_TEXT).assertIsDisplayed()
    }

    @Test
    fun bookListIsVisibleWhenBooksExist() {
        composeRule.setContent {
            MainScreen(
                books = listOf(
                    NovelBook(id = "1", title = "One", epubFileName = "1.epub"),
                ),
                statusMessage = "",
                isLoading = false,
                snackbarHostState = SnackbarHostState(),
                onImportClick = {},
                onBookClick = {},
            )
        }

        composeRule.onNodeWithTag(MainScreenTags.BOOK_LIST).assertIsDisplayed()
    }

    @Test
    fun loadingIndicatorIsVisibleWhenLoading() {
        composeRule.setContent {
            MainScreen(
                books = emptyList(),
                statusMessage = "",
                isLoading = true,
                snackbarHostState = SnackbarHostState(),
                onImportClick = {},
                onBookClick = {},
            )
        }

        composeRule.onNodeWithTag(MainScreenTags.LOADING).assertIsDisplayed()
    }
}
