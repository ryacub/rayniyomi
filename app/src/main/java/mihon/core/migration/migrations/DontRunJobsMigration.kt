package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.getAndSet

class DontRunJobsMigration : Migration {
    override val version = 105f

    // Don't run automatic backup or library update jobs if battery is low
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val libraryPreferences = migrationContext.graph?.libraryPreferences ?: return false

        val pref = libraryPreferences.autoUpdateDeviceRestrictions()
        if (pref.isSet() && "battery_not_low" in pref.get()) {
            pref.getAndSet { it - "battery_not_low" }
        }

        return true
    }
}
