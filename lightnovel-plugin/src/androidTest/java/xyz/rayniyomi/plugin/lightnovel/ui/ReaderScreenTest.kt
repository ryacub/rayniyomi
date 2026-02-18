package xyz.rayniyomi.plugin.lightnovel.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test

class ReaderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chapterIndicatorAndNavigationButtonsRender() {
        composeRule.setContent {
            ReaderScreen(
                title = "Book",
                chapterIndicator = "Chapter 1 / 3",
                chapterText = "content ".repeat(200),
                previousEnabled = false,
                nextEnabled = true,
                restoreOffset = 0,
                onPreviousClick = {},
                onNextClick = {},
                onPersistOffset = {},
            )
        }

        composeRule.onNodeWithTag(ReaderScreenTags.CHAPTER_INDICATOR).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderScreenTags.CHAPTER_TEXT).assertIsDisplayed()
        composeRule.onNodeWithTag(ReaderScreenTags.PREVIOUS_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(ReaderScreenTags.NEXT_BUTTON).assertIsEnabled()
    }
}
