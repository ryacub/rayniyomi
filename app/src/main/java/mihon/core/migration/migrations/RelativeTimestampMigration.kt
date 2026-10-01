package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class RelativeTimestampMigration : Migration {
    override val version = 106f

    // Bring back simplified relative timestamp setting
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.graph?.preferenceStore ?: return false
        val uiPreferences = migrationContext.graph.uiPreferences

        val pref = preferenceStore.getInt("relative_time", 7)
        if (pref.get() == 0) {
            uiPreferences.relativeTime().set(false)
        }

        return true
    }
}
