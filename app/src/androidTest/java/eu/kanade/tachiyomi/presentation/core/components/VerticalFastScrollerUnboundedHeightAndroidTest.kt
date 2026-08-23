package eu.kanade.tachiyomi.presentation.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
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
 * measure their content with those constraints, and the inner lazy layouts threw
 * IllegalStateException. These tests hold the guard that keeps composition alive instead. The
 * content must be a real lazy layout: a plain Text measures fine under unbounded height and would
 * not catch a lost guard.
 */
@RunWith(AndroidJUnit4::class)
class VerticalFastScrollerUnboundedHeightAndroidTest {

    private val listItems = (0 until 50).map { "item $it" }
    private val gridItems = (0 until 50).map { "cell $it" }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun list_scroller_composes_under_unbounded_height() {
        composeRule.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("outside")
                    val listState = rememberLazyListState()
                    VerticalFastScroller(listState = listState) {
                        LazyColumn(state = listState) {
                            items(listItems) { Text(it) }
                        }
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
                    val gridState = rememberLazyGridState()
                    VerticalGridFastScroller(
                        state = gridState,
                        columns = GridCells.Fixed(2),
                        arrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(8.dp),
                    ) {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(8.dp),
                        ) {
                            items(gridItems) { Text(it) }
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithText("outside").assertExists()
    }
}
