package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import java.io.File

class InternalChapterCacheDirMigration : Migration {
    override val version = 15f

    // Delete internal chapter cache dir.
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.graph?.application ?: return false

        File(context.cacheDir, "chapter_disk_cache").deleteRecursively()

        return true
    }
}
