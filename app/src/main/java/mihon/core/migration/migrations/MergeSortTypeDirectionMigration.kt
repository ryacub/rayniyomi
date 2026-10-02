package mihon.core.migration.migrations

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class MergeSortTypeDirectionMigration : Migration {
    override val version = 82f

    // Merge Sort Type and Direction into one class
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.graph?.application ?: return false
        val libraryPreferences = migrationContext.graph.libraryPreferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        prefs.edit {
            val mangasort = prefs.getString(
                libraryPreferences.mangaSortingMode().key(),
                null,
            ) ?: return@edit
            val animesort = prefs.getString(
                libraryPreferences.animeSortingMode().key(),
                null,
            ) ?: return@edit
            val direction = prefs.getString("library_sorting_ascending", "ASCENDING")!!
            putString(libraryPreferences.mangaSortingMode().key(), "$mangasort,$direction")
            putString(libraryPreferences.animeSortingMode().key(), "$animesort,$direction")
            remove("library_sorting_ascending")
        }

        return true
    }
}
