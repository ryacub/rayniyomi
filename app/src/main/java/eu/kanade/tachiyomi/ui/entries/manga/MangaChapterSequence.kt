package eu.kanade.tachiyomi.ui.entries.manga

import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaSourceLink
import tachiyomi.domain.items.chapter.model.Chapter

data class MangaChapterVersion(
    val manga: Manga,
    val chapter: Chapter,
    val sourceName: String,
)

data class MangaChapterGroup(
    val versions: List<MangaChapterVersion>,
) {
    val representative: MangaChapterVersion
        get() = versions.first()
}

/** Shared projection for a linked pair. Every returned row retains its original chapter owner. */
internal object MangaChapterSequence {

    fun project(
        firstManga: Manga,
        firstChapters: List<Chapter>,
        firstSourceName: String,
        secondManga: Manga,
        secondChapters: List<Chapter>,
        secondSourceName: String,
        link: MangaSourceLink,
        descending: Boolean,
    ): List<MangaChapterGroup> {
        val first = firstChapters.map { firstManga to it }
        val second = secondChapters.map { secondManga to it }
        val linkOrder = listOf(link.firstMangaId, link.secondMangaId)
        val sourceNames = mapOf(firstManga.id to firstSourceName, secondManga.id to secondSourceName)
        val numbered = (first + second)
            .filter { it.second.isRecognizedNumber }
            .groupBy { it.second.chapterNumber }
            .toSortedMap(if (descending) compareByDescending { it } else compareBy { it })
            .flatMap { (_, entries) ->
                val firstSourceEntries = entries.filter { it.first.id == linkOrder[0] }
                    .sortedWith(chapterOrder())
                val secondSourceEntries = entries.filter { it.first.id == linkOrder[1] }
                    .sortedWith(chapterOrder())
                if (firstSourceEntries.isNotEmpty() && secondSourceEntries.isNotEmpty()) {
                    val versions = (firstSourceEntries + secondSourceEntries).map { (manga, item) ->
                        MangaChapterVersion(manga, item, sourceNames[manga.id].orEmpty())
                    }
                    listOf(MangaChapterGroup(versions))
                } else {
                    entries.sortedWith(
                        compareBy({ linkOrder.indexOf(it.first.id) }, { it.second.sourceOrder }, { it.second.id }),
                    ).map { (manga, chapter) ->
                        MangaChapterGroup(
                            listOf(MangaChapterVersion(manga, chapter, sourceNames[manga.id].orEmpty())),
                        )
                    }
                }
            }

        val unnumbered = (first + second)
            .filterNot { it.second.isRecognizedNumber }
            .sortedWith(compareBy({ linkOrder.indexOf(it.first.id) }, { it.second.sourceOrder }, { it.second.id }))
            .map { (manga, chapter) ->
                MangaChapterGroup(listOf(MangaChapterVersion(manga, chapter, sourceNames[manga.id].orEmpty())))
            }

        return numbered + unnumbered
    }

    private fun chapterOrder() = compareBy<Pair<Manga, Chapter>>(
        { it.second.sourceOrder },
        { it.second.id },
    )
}
