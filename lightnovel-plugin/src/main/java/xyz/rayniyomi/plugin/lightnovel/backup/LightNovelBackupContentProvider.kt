package xyz.rayniyomi.plugin.lightnovel.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import xyz.rayniyomi.plugin.lightnovel.data.NovelStorage

class LightNovelBackupContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        return if (uri.pathSegments.size == 1 && uri.pathSegments[0] == PATH_LIBRARY) {
            exportLibraryAsCursor(projection)
        } else {
            null
        }
    }

    private fun exportLibraryAsCursor(projection: Array<String>?): Cursor {
        val storage = NovelStorage(context!!)
        val books = storage.listBooks()

        val columnsToUse = if (projection != null && projection.isNotEmpty()) {
            projection.intersect(COLUMNS.toSet()).toTypedArray()
        } else {
            COLUMNS
        }

        val cursor = MatrixCursor(columnsToUse)

        books.forEach { book ->
            val row = columnsToUse.map { column ->
                when (column) {
                    COLUMN_ID -> book.id
                    COLUMN_TITLE -> book.title
                    COLUMN_EPUB_FILE_NAME -> book.epubFileName
                    COLUMN_LAST_READ_CHAPTER -> book.lastReadChapter
                    COLUMN_LAST_READ_OFFSET -> book.lastReadOffset
                    COLUMN_UPDATED_AT -> book.updatedAt
                    else -> null
                }
            }.toTypedArray()
            cursor.addRow(row)
        }

        return cursor
    }

    override fun getType(uri: Uri): String? {
        return if (uri.pathSegments.size == 1 && uri.pathSegments[0] == PATH_LIBRARY) {
            "vnd.android.cursor.dir/vnd.xyz.rayniyomi.plugin.lightnovel.backup.library"
        } else {
            null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "xyz.rayniyomi.plugin.lightnovel.backup"
        const val PATH_LIBRARY = "library"

        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/$PATH_LIBRARY")

        val COLUMNS = arrayOf(
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
