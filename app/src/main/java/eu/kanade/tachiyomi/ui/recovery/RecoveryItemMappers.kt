package eu.kanade.tachiyomi.ui.recovery

import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.source.model.SChapter
import tachiyomi.domain.items.chapter.service.ChapterRecognition
import tachiyomi.domain.items.episode.service.EpisodeRecognition
import tachiyomi.domain.recovery.model.RecoveryItem

@JvmName("chaptersToRecoveryItems")
fun List<SChapter>.toRecoveryItems(
    mangaTitle: String,
): List<RecoveryItem> = distinctBy { it.url }.mapIndexed { index, chapter ->
    RecoveryItem(
        url = chapter.url,
        name = chapter.name,
        number = ChapterRecognition.parseChapterNumber(
            mangaTitle,
            chapter.name,
            chapter.chapter_number.toString().toDouble(),
        ),
        scanlator = chapter.scanlator,
        dateUpload = chapter.date_upload,
        sourceOrder = index.toLong(),
    )
}

@JvmName("episodesToRecoveryItems")
fun List<SEpisode>.toRecoveryItems(
    animeTitle: String,
): List<RecoveryItem> = distinctBy { it.url }.mapIndexed { index, episode ->
    RecoveryItem(
        url = episode.url,
        name = episode.name,
        number = EpisodeRecognition.parseEpisodeNumber(
            animeTitle,
            episode.name,
            episode.episode_number.toString().toDouble(),
        ),
        scanlator = episode.scanlator,
        dateUpload = episode.date_upload,
        sourceOrder = index.toLong(),
    )
}
