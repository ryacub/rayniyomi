package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class LogOutMALMigration : Migration {
    override val version = 121f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val trackerManager = migrationContext.graph?.trackerManager ?: return false

        if (trackerManager.myAnimeList.isLoggedIn) {
            trackerManager.myAnimeList.logout()
        }

        return true
    }
}
