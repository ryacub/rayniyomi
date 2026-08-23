package eu.kanade.tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.components.VerticalGridFastScroller

/**
 * A scrollable column hands its children unbounded height constraints. The fast scrollers used to
 * measure their content with those constraints, and the inner lazy layouts threw. These tests hold
 * the guard that keeps composition alive instead.
 */
@RunWith(AndroidJUnit4::class)
class VerticalFastScrollerUnboundedHeightAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun list_scroller_composes_under_unbounded_height() {
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("outside")
                    VerticalFastScroller(listState = rememberLazyListState()) {
                        Text("list content")
                    }
                }
            }
        }

        // The scroller reports zero size and skips its content under unbounded height.
        // A live sibling node proves composition did not crash.
        composeRule.onNodeWithText("outside").assertExists()
    }

    @Test
    fun grid_scroller_composes_under_unbounded_height() {
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("outside")
                    VerticalGridFastScroller(
                        state = rememberLazyGridState(),
                        columns = GridCells.Fixed(2),
                        arrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        Text("grid content")
                    }
                }
            }
        }

        composeRule.onNodeWithText("outside").assertExists()
    }
}
