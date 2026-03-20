package mihon.core.migration.migrations

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.domain.ui.UiPreferences
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import java.util.Locale

class SplitPreferencesMigration : Migration {
    override val version = 86f

    // Split the rest of the preferences in PreferencesHelper
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.get<Application>() ?: return false
        val uiPreferences = migrationContext.get<UiPreferences>() ?: return false
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        if (uiPreferences.themeMode().isSet()) {
            prefs.edit {
                val themeMode = prefs.getString(uiPreferences.themeMode().key(), null) ?: return@edit
                // Legacy installs may store lowercase enum values; normalize to current enum names.
                putString(uiPreferences.themeMode().key(), normalizeThemeModeValue(themeMode))
            }
        }

        return true
    }
}

internal fun normalizeThemeModeValue(themeMode: String): String {
    return themeMode.uppercase(Locale.ROOT)
}
