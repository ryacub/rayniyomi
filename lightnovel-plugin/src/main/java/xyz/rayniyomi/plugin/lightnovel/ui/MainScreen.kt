package xyz.rayniyomi.plugin.lightnovel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import xyz.rayniyomi.plugin.lightnovel.R
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook

object MainScreenTags {
    const val IMPORT_BUTTON = "main_import_button"
    const val EMPTY_TEXT = "main_empty_text"
    const val BOOK_LIST = "main_book_list"
    const val LOADING = "main_loading"
}

@Composable
internal fun MainScreen(
    books: List<NovelBook>,
    statusMessage: String,
    isLoading: Boolean,
    snackbarHostState: SnackbarHostState,
    onImportClick: () -> Unit,
    onBookClick: (NovelBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadingDescription = stringResource(R.string.loading)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.mediumSmall),
        ) {
            Button(
                onClick = onImportClick,
                enabled = !isLoading,
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

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(MainScreenTags.LOADING)
                        .semantics { contentDescription = loadingDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (books.isEmpty()) {
                EmptyScreen(
                    message = stringResource(R.string.no_books),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(MainScreenTags.EMPTY_TEXT),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(MainScreenTags.BOOK_LIST),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    items(books, key = { it.id }) { book ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBookClick(book) }
                                .padding(
                                    horizontal = MaterialTheme.padding.small,
                                    vertical = MaterialTheme.padding.mediumSmall,
                                ),
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
        isLoading = false,
        snackbarHostState = SnackbarHostState(),
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
        isLoading = false,
        snackbarHostState = SnackbarHostState(),
        onImportClick = {},
        onBookClick = {},
    )
}
