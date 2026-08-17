package eu.kanade.domain.source.manga.model

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

data class RemoteMangaUpdate(
    val manga: Manga,
    val newChapters: List<Chapter>,
)
