package mihon.core.migration.migrations

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class MoveChapterPreferencesMigration : Migration {
    override val version = 85f

    // Move chapter preferences from PreferencesHelper to LibraryPrefrences
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.graph?.application ?: return false
        val libraryPreferences = migrationContext.graph.libraryPreferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val preferences = listOf(
            libraryPreferences.filterChapterByRead(),
            libraryPreferences.filterChapterByDownloaded(),
            libraryPreferences.filterChapterByBookmarked(),
            libraryPreferences.sortChapterBySourceOrNumber(),
            libraryPreferences.displayChapterByNameOrNumber(),
            libraryPreferences.sortChapterByAscendingOrDescending(),
            libraryPreferences.filterEpisodeBySeen(),
            libraryPreferences.filterEpisodeByDownloaded(),
            libraryPreferences.filterEpisodeByBookmarked(),
            libraryPreferences.sortEpisodeBySourceOrNumber(),
            libraryPreferences.displayEpisodeByNameOrNumber(),
            libraryPreferences.sortEpisodeByAscendingOrDescending(),
        )

        prefs.edit {
            preferences.forEach { preference ->
                val key = preference.key()
                val value = prefs.getInt(key, Int.MIN_VALUE)
                if (value == Int.MIN_VALUE) return@forEach
                remove(key)
                putLong(key, value.toLong())
            }
        }

        return true
    }
}
