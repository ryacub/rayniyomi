package eu.kanade.presentation.entries.manga.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import eu.kanade.presentation.entries.components.EntryActionRow
import eu.kanade.presentation.entries.components.EntryInfoBox
import eu.kanade.presentation.entries.components.ExpandableEntryDescription
import tachiyomi.domain.entries.manga.model.Manga
import java.time.Instant

@Composable
fun MangaInfoBox(
    isTabletUi: Boolean,
    appBarPadding: Dp,
    manga: Manga,
    sourceName: String,
    isStubSource: Boolean,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    EntryInfoBox(
        isTabletUi = isTabletUi,
        appBarPadding = appBarPadding,
        coverData = manga,
        title = manga.title,
        author = manga.author,
        artist = manga.artist,
        status = manga.status,
        sourceName = sourceName,
        isStubSource = isStubSource,
        onCoverClick = onCoverClick,
        doSearch = doSearch,
        modifier = modifier,
    )
}

@Composable
fun MangaActionRow(
    favorite: Boolean,
    trackingCount: Int,
    nextUpdate: Instant?,
    isUserIntervalMode: Boolean,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: () -> Unit,
    onEditIntervalClicked: (() -> Unit)?,
    onEditCategory: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    EntryActionRow(
        favorite = favorite,
        trackingCount = trackingCount,
        nextUpdate = nextUpdate,
        isUserIntervalMode = isUserIntervalMode,
        onAddToLibraryClicked = onAddToLibraryClicked,
        onWebViewClicked = onWebViewClicked,
        onWebViewLongClicked = onWebViewLongClicked,
        onTrackingClicked = onTrackingClicked,
        onEditIntervalClicked = onEditIntervalClicked,
        onEditCategory = onEditCategory,
        modifier = modifier,
    )
}

@Composable
fun ExpandableMangaDescription(
    defaultExpandState: Boolean,
    description: String?,
    tagsProvider: () -> List<String>?,
    onTagSearch: (String) -> Unit,
    onCopyTagToClipboard: (tag: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExpandableEntryDescription(
        defaultExpandState = defaultExpandState,
        description = description,
        tagsProvider = tagsProvider,
        onTagSearch = onTagSearch,
        onCopyTagToClipboard = onCopyTagToClipboard,
        modifier = modifier,
    )
}
