package eu.kanade.presentation.browse.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.entries.components.BaseEntryListItem
import eu.kanade.tachiyomi.ui.browse.manga.migration.manga.MigrateMangaScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MigrateMangaScreen(
    navigateUp: () -> Unit,
    title: String?,
    state: MigrateMangaScreenModel.State,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
    onClickRecover: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = title,
                navigateUp = navigateUp,
                actions = {
                    if (!state.isEmpty) {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = stringResource(MR.strings.recovery_title),
                                    icon = Icons.Outlined.SwapVert,
                                    onClick = onClickRecover,
                                ),
                            ),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        if (state.isEmpty) {
            EmptyScreen(
                stringRes = MR.strings.empty_screen,
                modifier = Modifier.padding(contentPadding),
            )
            return@Scaffold
        }

        MigrateMangaContent(
            contentPadding = contentPadding,
            state = state,
            onClickItem = onClickItem,
            onClickCover = onClickCover,
        )
    }
}

@Composable
private fun MigrateMangaContent(
    contentPadding: PaddingValues,
    state: MigrateMangaScreenModel.State,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(state.titles) { manga ->
            MigrateMangaItem(
                manga = manga,
                onClickItem = onClickItem,
                onClickCover = onClickCover,
            )
        }
    }
}

@Composable
private fun MigrateMangaItem(
    manga: Manga,
    onClickItem: (Manga) -> Unit,
    onClickCover: (Manga) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseEntryListItem(
        modifier = modifier,
        coverData = manga,
        title = manga.title,
        onClickItem = { onClickItem(manga) },
        onClickCover = { onClickCover(manga) },
    )
}
