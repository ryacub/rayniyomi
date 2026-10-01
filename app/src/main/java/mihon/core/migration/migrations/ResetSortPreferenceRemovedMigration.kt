package mihon.core.migration.migrations

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class ResetSortPreferenceRemovedMigration : Migration {
    override val version = 44f

    // Reset sorting preference if using removed sort by source
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.graph?.application ?: return false
        val libraryPreferences = migrationContext.graph.libraryPreferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val oldMangaSortingMode = prefs.getInt(
            libraryPreferences.mangaSortingMode().key(),
            0,
        )

        if (oldMangaSortingMode == 5) { // SOURCE = 5
            prefs.edit {
                putInt(libraryPreferences.mangaSortingMode().key(), 0) // ALPHABETICAL = 0
            }
        }

        val oldAnimeSortingMode = prefs.getInt(
            libraryPreferences.animeSortingMode().key(),
            0,
        )

        if (oldAnimeSortingMode == 5) { // SOURCE = 5
            prefs.edit {
                putInt(libraryPreferences.animeSortingMode().key(), 0) // ALPHABETICAL = 0
            }
        }

        return true
    }
}
