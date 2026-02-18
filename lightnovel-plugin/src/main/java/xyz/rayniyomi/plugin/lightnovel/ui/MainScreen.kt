package xyz.rayniyomi.plugin.lightnovel.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import xyz.rayniyomi.plugin.lightnovel.R
import xyz.rayniyomi.plugin.lightnovel.data.NovelBook
import kotlin.random.Random

object MainScreenTags {
    const val IMPORT_BUTTON = "main_import_button"
    const val EMPTY_TEXT = "main_empty_text"
    const val EMPTY_FACE = "main_empty_face"
    const val BOOK_LIST = "main_book_list"
    const val LOADING = "main_loading"
}

@Composable
internal fun MainScreen(
    books: List<NovelBook>,
    statusMessage: String,
    isLoading: Boolean,
    onImportClick: () -> Unit,
    onBookClick: (NovelBook) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacingSmall = dimensionResource(id = R.dimen.ln_spacing_small)
    val spacingMedium = dimensionResource(id = R.dimen.ln_spacing_medium)
    val spacingLarge = dimensionResource(id = R.dimen.ln_spacing_large)

    Surface {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(spacingLarge),
            verticalArrangement = Arrangement.spacedBy(spacingMedium),
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
                        .testTag(MainScreenTags.LOADING),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (books.isEmpty()) {
                val face = remember { getRandomErrorFace() }
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = face,
                        modifier = Modifier.testTag(MainScreenTags.EMPTY_FACE),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.no_books),
                        modifier = Modifier
                            .fillMaxWidth()
                            .paddingFromBaseline(top = spacingLarge)
                            .testTag(MainScreenTags.EMPTY_TEXT),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(MainScreenTags.BOOK_LIST),
                    verticalArrangement = Arrangement.spacedBy(spacingSmall),
                ) {
                    items(books, key = { it.id }) { book ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onBookClick(book) }
                                .padding(horizontal = spacingSmall, vertical = spacingMedium),
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
        onImportClick = {},
        onBookClick = {},
    )
}

private val ErrorFaces = listOf(
    "(･o･;)",
    "Σ(ಠ_ಠ)",
    "ಥ_ಥ",
    "(˘･_･˘)",
    "(；￣Д￣)",
    "(･Д･。",
    "(╬ಠ益ಠ)",
    "(╥﹏╥)",
    "(⋟﹏⋞)",
    "Ò︵Ó",
    " ˙ᯅ˙)",
    "(¬_¬)",
)

private fun getRandomErrorFace(): String {
    return ErrorFaces[Random.nextInt(ErrorFaces.size)]
}

@Preview(showBackground = true)
@Composable
private fun MainScreenEmptyPreview() {
    MainScreen(
        books = emptyList(),
        statusMessage = "Failed to import EPUB",
        isLoading = false,
        onImportClick = {},
        onBookClick = {},
    )
}
