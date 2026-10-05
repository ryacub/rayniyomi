package eu.kanade.tachiyomi.data.library

import androidx.work.Data
import androidx.work.WorkInfo

internal data class LibraryUpdateProgress(
    val activeTitles: List<String>,
    val completed: Int,
    val total: Int,
) {

    fun toWorkData(): Data = Data.Builder()
        .putStringArray(
            KEY_ACTIVE_TITLES,
            activeTitles
                .take(MAX_ACTIVE_TITLES)
                .map { it.take(MAX_TITLE_LENGTH) }
                .toTypedArray(),
        )
        .putInt(KEY_COMPLETED, completed)
        .putInt(KEY_TOTAL, total)
        .build()

    companion object {
        fun from(workInfo: WorkInfo): LibraryUpdateProgress? {
            if (workInfo.state != WorkInfo.State.RUNNING) return null

            val progress = workInfo.progress
            return LibraryUpdateProgress(
                activeTitles = progress.getStringArray(KEY_ACTIVE_TITLES)?.toList().orEmpty(),
                completed = progress.getInt(KEY_COMPLETED, 0),
                total = progress.getInt(KEY_TOTAL, 0),
            )
        }
    }
}

internal fun List<WorkInfo>.toLibraryUpdateProgressOrNull(): LibraryUpdateProgress? =
    firstNotNullOfOrNull(LibraryUpdateProgress::from)

private const val KEY_ACTIVE_TITLES = "libraryUpdateActiveTitles"
private const val KEY_COMPLETED = "libraryUpdateCompleted"
private const val KEY_TOTAL = "libraryUpdateTotal"
private const val MAX_ACTIVE_TITLES = 5
private const val MAX_TITLE_LENGTH = 256
