package eu.kanade.presentation.entries.manga

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.ResumeFabText
import eu.kanade.presentation.util.formatChapterNumber
import eu.kanade.presentation.util.padItemNumber
import eu.kanade.tachiyomi.ui.entries.manga.ChapterList
import eu.kanade.tachiyomi.ui.entries.manga.MangaChapterVersion
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreenModel
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun MangaSourcesStatus(
    state: MangaScreenModel.State.Success,
    onFindAnotherSource: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(MR.strings.link_manga_source_section),
            modifier = Modifier.semantics { heading() },
        )
        val linkedSourceName = state.linkedSourceName
        Text(
            if (linkedSourceName == null) {
                state.source.name
            } else {
                stringResource(MR.strings.link_manga_source_pair_label, state.source.name, linkedSourceName)
            },
        )
        if (state.linkedSourceUnavailable) {
            Text(stringResource(MR.strings.link_manga_source_unavailable_status, state.linkedSourceName.orEmpty()))
        } else if (state.linkedSourceRefreshFailed) {
            Text(stringResource(MR.strings.link_manga_source_refresh_failed, state.linkedSourceName.orEmpty()))
        }
        if (state.sourceUnavailable) {
            Text(stringResource(MR.strings.link_manga_source_unavailable_status, state.source.name))
        } else if (state.sourceRefreshFailed) {
            Text(stringResource(MR.strings.link_manga_source_refresh_failed, state.source.name))
        }
        if (onFindAnotherSource != null) {
            TextButton(onClick = onFindAnotherSource) {
                Text(stringResource(MR.strings.link_manga_source_find_another))
            }
        }
    }
}

@Composable
internal fun LinkedMangaVersionDialog(
    versions: List<MangaChapterVersion>,
    onDismissRequest: () -> Unit,
    onVersionSelected: (MangaChapterVersion) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(MR.strings.link_manga_source_choose_version)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(versions, key = { it.chapter.id }) { version ->
                    val label = linkedMangaVersionLabel(version, versions)
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVersionSelected(version) }
                            .semantics { contentDescription = label }
                            .padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
internal fun linkedChapterSourceNames(versions: List<MangaChapterVersion>): String {
    val labels = mutableListOf<String>()
    for ((sourceName, sourceVersions) in versions.groupBy { it.sourceName }) {
        if (sourceVersions.size == 1) {
            labels += sourceName
            continue
        }
        val details = sourceVersions.map { it.chapter.versionDetail() }
        val duplicateDetails = details.distinct().size != details.size
        sourceVersions.forEachIndexed { index, version ->
            val detail = details[index]
                ?: stringResource(MR.strings.link_manga_source_version_number, index + 1)
            val disambiguator = if (duplicateDetails) {
                stringResource(MR.strings.link_manga_source_version_number, index + 1)
            } else {
                detail
            }
            labels += "$sourceName · $disambiguator"
        }
    }
    return labels.distinct().joinToString(" · ")
}

@Composable
internal fun linkedMangaVersionLabel(version: MangaChapterVersion, versions: List<MangaChapterVersion>): String {
    val chapter = version.chapter
    val number = chapter.takeIf { it.isRecognizedNumber }?.let {
        stringResource(MR.strings.display_mode_chapter, formatChapterNumber(it.chapterNumber))
    }
    val title = chapter.name.takeIf { it.isNotBlank() }
    val chapterLabel = listOfNotNull(number, title).distinct().joinToString(" · ")
    val status = if (chapter.read) {
        stringResource(
            MR.strings.link_manga_source_read,
        )
    } else {
        stringResource(MR.strings.unread)
    }
    val sameSourceVersions = versions.filter { it.sourceName == version.sourceName }
    val sameSourceIndex = sameSourceVersions.indexOfFirst { it.chapter.id == chapter.id }
    val sameSourceDetails = sameSourceVersions.map { it.chapter.versionDetail() }
    val sourceLabel = if (sameSourceVersions.size > 1) {
        val duplicateDetails = sameSourceDetails.distinct().size != sameSourceDetails.size
        val detail = sameSourceDetails[sameSourceIndex]
        val disambiguator = if (duplicateDetails || detail == null) {
            stringResource(MR.strings.link_manga_source_version_number, sameSourceIndex + 1)
        } else {
            detail
        }
        "${version.sourceName} · $disambiguator"
    } else {
        version.sourceName
    }
    return stringResource(MR.strings.link_manga_source_resume_choice, chapterLabel, sourceLabel, status)
}

@Composable
internal fun ChapterList.Item.sourceLabel(): String? = versions
    .takeIf { it.isNotEmpty() }
    ?.let { linkedChapterSourceNames(it) }
    ?: sourceName

@Composable
private fun Chapter.versionDetail(): String? {
    val scanlator = scanlator?.takeIf(String::isNotBlank)
    val number = takeIf { isRecognizedNumber }?.let {
        stringResource(MR.strings.display_mode_chapter, formatChapterNumber(it.chapterNumber))
    }
    return scanlator ?: name.takeIf { it.isNotBlank() && it != number }
}

@Composable
internal fun ResumeChapterFabText(chapter: Chapter?, isLinked: Boolean) {
    val number = chapter?.takeIf { it.isRecognizedNumber }?.chapterNumber
    ResumeFabText(
        canResume = chapter != null || isLinked,
        detail = number?.let { "C${formatChapterNumber(it).padItemNumber()}" },
    )
}
