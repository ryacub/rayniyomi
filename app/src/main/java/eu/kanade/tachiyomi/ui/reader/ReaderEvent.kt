package eu.kanade.tachiyomi.ui.reader

import android.net.Uri
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage

sealed interface ReaderEvent {
    data object ReloadViewerChapters : ReaderEvent
    data object PageChanged : ReaderEvent
    data class SetOrientation(val orientation: Int) : ReaderEvent
    data class SetCoverResult(val result: SetAsCoverResult) : ReaderEvent

    data class SavedImage(val result: SaveImageResult) : ReaderEvent
    data class ShareImage(val uri: Uri, val page: ReaderPage) : ReaderEvent
    data class CopyImage(val uri: Uri) : ReaderEvent
}

enum class SetAsCoverResult {
    Success,
    AddToLibraryFirst,
    Error,
}

sealed interface SaveImageResult {
    class Success(val uri: Uri) : SaveImageResult
    class Error(val error: Throwable) : SaveImageResult
}
