package xyz.rayniyomi.plugin.lightnovel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import xyz.rayniyomi.plugin.lightnovel.R
import xyz.rayniyomi.plugin.lightnovel.reader.ReaderProgressMapper

object ReaderScreenTags {
    const val PREVIOUS_BUTTON = "reader_previous_button"
    const val NEXT_BUTTON = "reader_next_button"
    const val CHAPTER_INDICATOR = "reader_chapter_indicator"
    const val CHAPTER_TEXT = "reader_chapter_text"
}

@Composable
internal fun ReaderScreen(
    title: String,
    chapterIndicator: String,
    chapterText: String,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    restoreOffset: Int,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onPersistOffset: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(chapterText, restoreOffset) {
        scrollState.scrollTo(0)
        if (restoreOffset <= 0 || chapterText.isEmpty()) {
            return@LaunchedEffect
        }
        val maxScroll = withTimeoutOrNull(500) {
            snapshotFlow { scrollState.maxValue }
                .filter { it > 0 }
                .first()
        } ?: scrollState.maxValue
        if (maxScroll <= 0) return@LaunchedEffect
        val targetScrollY = ReaderProgressMapper.offsetToScrollY(
            charOffset = restoreOffset,
            chapterLength = chapterText.length,
            maxScrollY = maxScroll,
        )
        scrollState.scrollTo(targetScrollY)
    }

    LaunchedEffect(chapterText) {
        if (chapterText.isEmpty()) return@LaunchedEffect

        snapshotFlow { scrollState.value to scrollState.maxValue }
            .debounce(250)
            .collect { (scrollY, maxScrollY) ->
                val offset = ReaderProgressMapper.scrollYToOffset(
                    scrollY = scrollY,
                    chapterLength = chapterText.length,
                    maxScrollY = maxScrollY,
                )
                onPersistOffset(offset)
            }
    }

    Surface {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onPreviousClick,
                    enabled = previousEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ReaderScreenTags.PREVIOUS_BUTTON),
                ) {
                    Text(text = stringResource(R.string.previous_chapter))
                }

                Text(
                    text = chapterIndicator,
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .testTag(ReaderScreenTags.CHAPTER_INDICATOR),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Button(
                    onClick = onNextClick,
                    enabled = nextEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(ReaderScreenTags.NEXT_BUTTON),
                ) {
                    Text(text = stringResource(R.string.next_chapter))
                }
            }

            Text(
                text = chapterText,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .testTag(ReaderScreenTags.CHAPTER_TEXT),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReaderScreenPreview() {
    val paragraph = remember {
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit. ".repeat(25)
    }
    ReaderScreen(
        title = "Sample Book",
        chapterIndicator = "Chapter 1 / 4",
        chapterText = paragraph,
        previousEnabled = false,
        nextEnabled = true,
        restoreOffset = 0,
        onPreviousClick = {},
        onNextClick = {},
        onPersistOffset = {},
    )
}
