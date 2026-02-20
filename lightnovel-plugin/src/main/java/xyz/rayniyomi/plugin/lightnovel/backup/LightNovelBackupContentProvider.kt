package xyz.rayniyomi.plugin.lightnovel.backup

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.util.Log
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
        if (!isCallerTrusted()) {
            Log.w(TAG, "query() rejected: caller UID ${Binder.getCallingUid()} is not trusted")
            return null
        }
        return if (uri.pathSegments.size == 1 && uri.pathSegments[0] == PATH_LIBRARY) {
            exportLibraryAsCursor(projection)
        } else {
            null
        }
    }

    /**
     * Returns true only if the caller is the host app, verified by certificate signature match.
     * This ensures only apps signed with the same certificate (i.e. the host app) can access
     * the provider, regardless of package name or UID spoofing.
     */
    private fun isCallerTrusted(): Boolean {
        val callingUid = Binder.getCallingUid()
        // Allow the plugin itself (same process calls)
        if (callingUid == Process.myUid()) return true
        return try {
            val ctx = requireNotNull(context)
            val callerPackages = ctx.packageManager.getPackagesForUid(callingUid) ?: return false
            callerPackages.any { packageName ->
                ctx.packageManager.checkSignatures(packageName, ctx.packageName) ==
                    PackageManager.SIGNATURE_MATCH
            }
        } catch (e: Exception) {
            Log.w(TAG, "isCallerTrusted: signature check failed", e)
            false
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

    override fun call(
        method: String,
        arg: String?,
        extras: Bundle?,
    ): Bundle? {
        if (!isCallerTrusted()) {
            Log.w(TAG, "call() rejected: caller UID ${Binder.getCallingUid()} is not trusted")
            return Bundle().apply { putBoolean(RESULT_SUCCESS, false) }
        }
        if (method != METHOD_RESTORE_BACKUP) return super.call(method, arg, extras)

        val backupData = extras?.getByteArray(EXTRA_BACKUP_DATA)
        if (backupData == null) {
            return Bundle().apply { putBoolean(RESULT_SUCCESS, false) }
        }

        val restored = runCatching {
            BackupLightNovelRestorer(requireNotNull(context)).restoreBackup(backupData)
        }.getOrElse { error ->
            Log.e(TAG, "Failed to restore backup payload", error)
            false
        }
        return Bundle().apply { putBoolean(RESULT_SUCCESS, restored) }
    }

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

        const val METHOD_RESTORE_BACKUP = "restore_backup"
        const val EXTRA_BACKUP_DATA = "backup_data"
        const val RESULT_SUCCESS = "success"
        private const val TAG = "LNBackupProvider"
    }
}
