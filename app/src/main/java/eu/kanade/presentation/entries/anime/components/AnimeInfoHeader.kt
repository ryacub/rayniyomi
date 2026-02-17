package eu.kanade.presentation.entries.anime.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import eu.kanade.presentation.entries.components.EntryActionRow
import eu.kanade.presentation.entries.components.EntryInfoBox
import eu.kanade.presentation.entries.components.ExpandableEntryDescription
import eu.kanade.tachiyomi.data.coil.useBackground
import tachiyomi.domain.entries.anime.model.Anime
import java.time.Instant

@Composable
fun AnimeInfoBox(
    isTabletUi: Boolean,
    appBarPadding: Dp,
    anime: Anime,
    sourceName: String,
    isStubSource: Boolean,
    onCoverClick: () -> Unit,
    doSearch: (query: String, global: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    EntryInfoBox(
        isTabletUi = isTabletUi,
        appBarPadding = appBarPadding,
        coverData = anime,
        title = anime.title,
        author = anime.author,
        artist = anime.artist,
        status = anime.status,
        sourceName = sourceName,
        isStubSource = isStubSource,
        onCoverClick = onCoverClick,
        doSearch = doSearch,
        modifier = modifier,
        imageRequestBuilder = { useBackground(true) },
    )
}

@Composable
fun AnimeActionRow(
    favorite: Boolean,
    trackingCount: Int,
    nextUpdate: Instant?,
    isUserIntervalMode: Boolean,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
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
fun ExpandableAnimeDescription(
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
