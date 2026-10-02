package mihon.core.migration.migrations

import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class EnableAutoBackupMigration : Migration {
    override val version = 84f

    // Always attempt automatic backup creation
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.graph?.application ?: return false
        val backupPreferences = migrationContext.graph.backupPreferences

        if (backupPreferences.backupInterval().get() == 0) {
            backupPreferences.backupInterval().set(12)
            BackupCreateJob.setupTask(context)
        }

        return true
    }
}
