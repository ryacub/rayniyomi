package eu.kanade.presentation.browse.anime

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
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.MigrateAnimeScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen

@Composable
fun MigrateAnimeScreen(
    navigateUp: () -> Unit,
    title: String?,
    state: MigrateAnimeScreenModel.State,
    onClickItem: (Anime) -> Unit,
    onClickCover: (Anime) -> Unit,
    onClickRecover: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = title,
                navigateUp = navigateUp,
                actions = {
                    AppBarActions(
                        persistentListOf(
                            AppBar.Action(
                                title = stringResource(MR.strings.recovery_title),
                                icon = Icons.Outlined.SwapVert,
                                onClick = onClickRecover,
                            ),
                        ),
                    )
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

        MigrateAnimeContent(
            contentPadding = contentPadding,
            state = state,
            onClickItem = onClickItem,
            onClickCover = onClickCover,
        )
    }
}

@Composable
private fun MigrateAnimeContent(
    contentPadding: PaddingValues,
    state: MigrateAnimeScreenModel.State,
    onClickItem: (Anime) -> Unit,
    onClickCover: (Anime) -> Unit,
) {
    FastScrollLazyColumn(
        contentPadding = contentPadding,
    ) {
        items(state.titles) { anime ->
            MigrateAnimeItem(
                anime = anime,
                onClickItem = onClickItem,
                onClickCover = onClickCover,
            )
        }
    }
}

@Composable
private fun MigrateAnimeItem(
    anime: Anime,
    onClickItem: (Anime) -> Unit,
    onClickCover: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    BaseEntryListItem(
        modifier = modifier,
        coverData = anime,
        title = anime.title,
        onClickItem = { onClickItem(anime) },
        onClickCover = { onClickCover(anime) },
    )
}
