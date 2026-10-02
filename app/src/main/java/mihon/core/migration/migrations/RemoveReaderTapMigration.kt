package mihon.core.migration.migrations

import androidx.preference.PreferenceManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class RemoveReaderTapMigration : Migration {
    override val version = 77f

    // Remove reader tapping option in favor of disabled nav layouts
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.graph?.application ?: return false
        val readerPreferences = migrationContext.graph.readerPreferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val oldReaderTap = prefs.getBoolean("reader_tap", false)
        if (!oldReaderTap) {
            readerPreferences.navigationModePager().set(5)
            readerPreferences.navigationModeWebtoon().set(5)
        }

        return true
    }
}
