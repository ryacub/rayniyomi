package xyz.rayniyomi.plugin.lightnovel.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Uri
import android.database.Cursor
import android.database.MatrixCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage

class LightNovelBackupContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        return if (uri.pathSegments.size == 1 && uri.pathSegments[0] == PATH_LIBRARY) {
            exportLibraryAsCursor()
        } else {
            null
        }
    }

    fun getType(uri: Uri): String? = null

    fun insert(uri: Uri, values: ContentValues?): Uri? = null

    fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    private suspend fun exportLibraryAsCursor(): Cursor {
        val storage = NovelStorage(context!!)
        val library = withContext(Dispatchers.IO) {
            storage.readLibrary()
        }

        val rows = library.books.map { book ->
            arrayOf<Any>(
                book.id,
                book.title,
                book.epubFileName,
                book.lastReadChapter,
                book.lastReadOffset,
                book.updatedAt,
            )
        }

        return MatrixCursor(
            COLUMN_NAMES,
            rows.toTypedArray(),
        )
    }

    companion object {
        const val AUTHORITY = "xyz.rayniyomi.plugin.lightnovel.backup"
        const val PATH_LIBRARY = "library"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_LIBRARY")

        private val COLUMN_NAMES = arrayOf(
            COLUMN_ID,
            COLUMN_TITLE,
            COLUMN_EPUB_FILE_NAME,
            COLUMN_LAST_READ_CHAPTER,
            COLUMN_LAST_READ_OFFSET,
            COLUMN_UPDATED_AT,
        )

        const val COLUMN_ID = "id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_EPUB_FILE_NAME = "epub_file_name"
        const val COLUMN_LAST_READ_CHAPTER = "last_read_chapter"
        const val COLUMN_LAST_READ_OFFSET = "last_read_offset"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
