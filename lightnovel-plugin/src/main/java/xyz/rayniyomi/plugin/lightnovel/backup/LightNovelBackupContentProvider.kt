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
import xyz.rayniyomi.lightnovel.contract.LightNovelBackupContract
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
        return if (uri.pathSegments.size == 1 &&
            uri.pathSegments[0] == LightNovelBackupContract.PATH_LIBRARY
        ) {
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
                    LightNovelBackupContract.COLUMN_ID -> book.id
                    LightNovelBackupContract.COLUMN_TITLE -> book.title
                    LightNovelBackupContract.COLUMN_EPUB_FILE_NAME -> book.epubFileName
                    LightNovelBackupContract.COLUMN_LAST_READ_CHAPTER -> book.lastReadChapter
                    LightNovelBackupContract.COLUMN_LAST_READ_OFFSET -> book.lastReadOffset
                    LightNovelBackupContract.COLUMN_UPDATED_AT -> book.updatedAt
                    else -> null
                }
            }.toTypedArray<Any?>()
            cursor.addRow(row)
        }

        return cursor
    }

    override fun getType(uri: Uri): String? {
        return if (uri.pathSegments.size == 1 &&
            uri.pathSegments[0] == LightNovelBackupContract.PATH_LIBRARY
        ) {
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
            return Bundle().apply { putBoolean(LightNovelBackupContract.RESULT_SUCCESS, false) }
        }
        if (method != LightNovelBackupContract.METHOD_RESTORE_BACKUP) return super.call(method, arg, extras)

        val backupData = extras?.getByteArray(LightNovelBackupContract.EXTRA_BACKUP_DATA)
        if (backupData == null) {
            return Bundle().apply { putBoolean(LightNovelBackupContract.RESULT_SUCCESS, false) }
        }

        val restored = runCatching {
            BackupLightNovelRestorer(requireNotNull(context)).restoreBackup(backupData)
        }.getOrElse { error ->
            Log.e(TAG, "Failed to restore backup payload", error)
            false
        }
        return Bundle().apply { putBoolean(LightNovelBackupContract.RESULT_SUCCESS, restored) }
    }

    companion object {
        const val AUTHORITY = LightNovelBackupContract.AUTHORITY
        const val PATH_LIBRARY = LightNovelBackupContract.PATH_LIBRARY

        val CONTENT_URI: Uri = Uri.parse(LightNovelBackupContract.CONTENT_URI)

        val COLUMNS = LightNovelBackupContract.LIBRARY_COLUMNS.toTypedArray()
        private const val TAG = "LNBackupProvider"
    }
}
