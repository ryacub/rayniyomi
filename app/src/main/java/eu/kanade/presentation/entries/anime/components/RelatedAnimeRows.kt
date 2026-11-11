package eu.kanade.presentation.entries.anime.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeRelationGroup
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.presentation.core.components.material.padding

@Composable
fun RelatedAnimeRows(
    relations: List<AnimeRelationGroup>,
    onClick: (Anime) -> Unit,
    onLongClick: (Anime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
        relations.forEach { relation ->
            Text(
                text = relation.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = MaterialTheme.padding.medium),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                items(relation.anime, key = { it.id }) { anime ->
                    Box(Modifier.width(96.dp)) {
                        EntryComfortableGridItem(
                            title = anime.title,
                            onClick = { onClick(anime) },
                            onLongClick = { onLongClick(anime) },
                            coverData = anime.asAnimeCover(),
                            coverAlpha = if (anime.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                            coverBadgeStart = { InLibraryBadge(enabled = anime.favorite) },
                        )
                    }
                }
            }
        }
    }
}
