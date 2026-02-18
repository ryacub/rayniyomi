package xyz.rayniyomi.plugin.lightnovel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.rayniyomi.plugin.lightnovel.R
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook

object MainScreenTags {
    const val IMPORT_BUTTON = "main_import_button"
    const val EMPTY_TEXT = "main_empty_text"
    const val BOOK_LIST = "main_book_list"
}

@Composable
internal fun MainScreen(
    books: List<NovelBook>,
    statusMessage: String,
    onImportClick: () -> Unit,
    onBookClick: (NovelBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onImportClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(MainScreenTags.IMPORT_BUTTON),
            ) {
                Text(text = stringResource(R.string.import_epub))
            }

            statusMessage.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (books.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_books),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MainScreenTags.EMPTY_TEXT),
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(MainScreenTags.BOOK_LIST),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(books, key = { it.id }) { book ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBookClick(book) }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = book.title,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    MainScreen(
        books = listOf(
            NovelBook(id = "1", title = "Sample 1", epubFileName = "1.epub"),
            NovelBook(id = "2", title = "Sample 2", epubFileName = "2.epub"),
        ),
        statusMessage = "",
        onImportClick = {},
        onBookClick = {},
    )
}

@Preview(showBackground = true)
@Composable
private fun MainScreenEmptyPreview() {
    MainScreen(
        books = emptyList(),
        statusMessage = "Failed to import EPUB",
        onImportClick = {},
        onBookClick = {},
    )
}
