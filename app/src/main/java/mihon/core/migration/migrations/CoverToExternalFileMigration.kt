package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import java.io.File

class CoverToExternalFileMigration : Migration {
    override val version = 19f

    // Move covers to external files dir.
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.graph?.application ?: return false

        val oldDir = File(context.externalCacheDir, "cover_disk_cache")
        if (oldDir.exists()) {
            val destDir = context.getExternalFilesDir("covers")
            if (destDir != null) {
                oldDir.listFiles()?.forEach {
                    it.renameTo(File(destDir, it.name))
                }
            }
        }

        return true
    }
}
