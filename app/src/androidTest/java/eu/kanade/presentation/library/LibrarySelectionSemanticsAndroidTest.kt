package eu.kanade.presentation.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.presentation.library.anime.AnimeLibraryList
import eu.kanade.presentation.library.manga.MangaLibraryComfortableGrid
import eu.kanade.presentation.library.manga.MangaLibraryCompactGrid
import eu.kanade.presentation.library.manga.MangaLibraryList
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga

@RunWith(AndroidJUnit4::class)
class LibrarySelectionSemanticsAndroidTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val first = libraryManga(id = 1, title = "First manga")
    private val second = libraryManga(id = 2, title = "Second manga")
    private val third = libraryManga(id = 3, title = "Third manga")
    private val mangaItems = listOf(first, second, third).map { MangaLibraryItem(it) }

    private val noSelectionState = SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected)

    @Test
    fun compactGridReportsEachTitleStateInSelectionMode() {
        setMangaContent { selection -> CompactGrid(selection) }

        title("First manga").assertIsSelected()
        title("Second manga").assertIsNotSelected()
    }

    @Test
    fun comfortableGridReportsEachTitleStateInSelectionMode() {
        setMangaContent { selection ->
            MangaLibraryComfortableGrid(
                items = mangaItems,
                columns = 2,
                contentPadding = PaddingValues(),
                selection = selection,
                onClick = {},
                onLongClick = {},
                onClickContinueReading = null,
                searchQuery = null,
                onGlobalSearchClicked = {},
            )
        }

        title("First manga").assertIsSelected()
        title("Second manga").assertIsNotSelected()
    }

    @Test
    fun listReportsEachTitleStateInSelectionMode() {
        setMangaContent { selection ->
            MangaLibraryList(
                items = mangaItems,
                rows = 0,
                containerHeight = 0,
                contentPadding = PaddingValues(),
                selection = selection,
                onClick = {},
                onLongClick = {},
                onClickContinueReading = null,
                searchQuery = null,
                onGlobalSearchClicked = {},
            )
        }

        title("First manga").assertIsSelected()
        title("Second manga").assertIsNotSelected()
    }

    @Test
    fun animeListReportsEachTitleStateInSelectionMode() {
        val watched = libraryAnime(id = 1, title = "First anime")
        val other = libraryAnime(id = 2, title = "Second anime")
        composeRule.setContent {
            Themed {
                AnimeLibraryList(
                    items = listOf(watched, other).map { AnimeLibraryItem(it) },
                    rows = 0,
                    containerHeight = 0,
                    contentPadding = PaddingValues(),
                    selection = listOf(watched),
                    onClick = {},
                    onLongClick = {},
                    onClickContinueWatching = null,
                    searchQuery = null,
                    onGlobalSearchClicked = {},
                )
            }
        }

        title("First anime").assertIsSelected()
        title("Second anime").assertIsNotSelected()
    }

    @Test
    fun titlesReportNoSelectionStateOutsideSelectionMode() {
        composeRule.setContent { Themed { CompactGrid(selection = emptyList()) } }

        title("First manga").assert(noSelectionState)
        title("Second manga").assert(noSelectionState)
    }

    @Test
    fun selectionChangesUpdateTheReportedState() {
        var selection by mutableStateOf(listOf(first, second))
        composeRule.setContent { Themed { CompactGrid(selection) } }

        selection = listOf(first, third)
        composeRule.waitForIdle()

        title("Second manga").assertIsNotSelected()
        title("Third manga").assertIsSelected()
    }

    private fun setMangaContent(content: @Composable (List<LibraryManga>) -> Unit) {
        composeRule.setContent { Themed { content(listOf(first)) } }
    }

    @Composable
    private fun CompactGrid(selection: List<LibraryManga>) {
        MangaLibraryCompactGrid(
            items = mangaItems,
            showTitle = true,
            columns = 2,
            contentPadding = PaddingValues(),
            selection = selection,
            onClick = {},
            onLongClick = {},
            onClickContinueReading = null,
            searchQuery = null,
            onGlobalSearchClicked = {},
        )
    }

    @Composable
    private fun Themed(content: @Composable () -> Unit) {
        MaterialTheme { Surface { content() } }
    }

    private fun title(text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasClickAction() and hasAnyDescendant(hasText(text)), useUnmergedTree = true)

    private fun libraryManga(id: Long, title: String) = LibraryManga(
        manga = Manga.create().copy(id = id, title = title, favorite = true),
        category = 0,
        totalChapters = 0,
        readCount = 0,
        bookmarkCount = 0,
        latestUpload = 0,
        chapterFetchedAt = 0,
        lastRead = 0,
    )

    private fun libraryAnime(id: Long, title: String) = LibraryAnime(
        anime = Anime.create().copy(id = id, title = title, favorite = true),
        category = 0,
        totalCount = 0,
        seenCount = 0,
        bookmarkCount = 0,
        fillermarkCount = 0,
        latestUpload = 0,
        episodeFetchedAt = 0,
        lastSeen = 0,
    )
}
