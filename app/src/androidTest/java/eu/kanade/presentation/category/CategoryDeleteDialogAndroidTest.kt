package eu.kanade.presentation.category

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.presentation.category.components.CategoryDeleteDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDeleteDialogAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmation_preserves_backslashes_before_quotes_in_the_category_name() {
        val category = """A \"B\""""

        composeRule.setContent {
            MaterialTheme {
                CategoryDeleteDialog(
                    onDismissRequest = {},
                    onDelete = {},
                    category = category,
                    hasChildren = false,
                )
            }
        }

        composeRule.onNodeWithText(category, substring = true).assertExists()
    }
}
