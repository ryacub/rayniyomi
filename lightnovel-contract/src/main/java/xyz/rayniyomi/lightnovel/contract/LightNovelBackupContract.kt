package xyz.rayniyomi.lightnovel.contract

/**
 * IPC contract for the host and light novel plugin backup boundary.
 */
object LightNovelBackupContract {
    const val AUTHORITY = "xyz.rayniyomi.plugin.lightnovel.backup"
    const val PATH_LIBRARY = "library"
    const val CONTENT_URI = "content://$AUTHORITY/$PATH_LIBRARY"

    const val METHOD_RESTORE_BACKUP = "restore_backup"
    const val EXTRA_BACKUP_DATA = "backup_data"
    const val RESULT_SUCCESS = "success"

    const val BACKUP_VERSION = 1
    const val MIN_COMPATIBLE_BACKUP_VERSION = 1
    const val LATEST_BACKUP_VERSION = 1

    const val COLUMN_ID = "id"
    const val COLUMN_TITLE = "title"
    const val COLUMN_EPUB_FILE_NAME = "epub_file_name"
    const val COLUMN_LAST_READ_CHAPTER = "last_read_chapter"
    const val COLUMN_LAST_READ_OFFSET = "last_read_offset"
    const val COLUMN_UPDATED_AT = "updated_at"

    val LIBRARY_COLUMNS = listOf(
        COLUMN_ID,
        COLUMN_TITLE,
        COLUMN_EPUB_FILE_NAME,
        COLUMN_LAST_READ_CHAPTER,
        COLUMN_LAST_READ_OFFSET,
        COLUMN_UPDATED_AT,
    )
}
